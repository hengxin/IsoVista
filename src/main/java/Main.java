import checker.Checker;
import collector.Collector;
import config.Config;
import generator.general.GeneralGenerator;
import history.serializer.TextHistorySerializer;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.reflections.Reflections;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;
import util.ConfigParser;
import util.Profiler;
import util.RuntimeDataSerializer;

import java.io.FileInputStream;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

@Slf4j
@Command(name = "DBTest", mixinStandardHelpOptions = true, version = "DBTest 0.1", description = "Test database isolation level.")
public class Main implements Callable<Integer> {

    @Parameters(index = "0", description = "Config file")
    private String configFile;

    @Override
    @SneakyThrows
    public Integer call() {
        log.info("DBTest start");
        var config = new Properties();
        var fileInputStream = new FileInputStream(configFile);
        config.load(fileInputStream);
        run(config);
        return 0;
    }

    @SneakyThrows
    public void run(Properties config) {
        var collectorReflections = new Reflections("collector");
        Set<Class<? extends Collector>> collectorSubClasses = collectorReflections.getSubTypesOf(Collector.class);
        Map<String, Class<? extends Collector>> collectors = new HashMap<>();
        for (var collectorImplClass : collectorSubClasses) {
            log.info("Find collector {}", collectorImplClass.getName());
            var name = (String) collectorImplClass.getDeclaredField("NAME").get(null);
            collectors.put(name.toLowerCase(), collectorImplClass);
        }

        var checkerReflections = new Reflections("checker");
        Set<Class<? extends Checker>> checkerImpls = checkerReflections.getSubTypesOf(Checker.class);
        Map<String, Class<? extends Checker>> checkers = new HashMap<>();
        for (var checkerImplClass : checkerImpls) {
            log.info("Find checker {}", checkerImplClass.getName());
            var name = (String) checkerImplClass.getDeclaredField("NAME").get(null);
            checkers.put(name.toLowerCase(), checkerImplClass);
        }

        var collector = collectors.get(config.getProperty(Config.DB_TYPE).toLowerCase());
        if (collector == null) {
            log.error("Can not find collector of {}", config.getProperty(Config.DB_TYPE));
            return;
        }

        var checker = checkers.get(config.getProperty(Config.CHECKER_TYPE).toLowerCase());
        if (checker == null) {
            log.error("Can not find checker {}", config.getProperty(Config.CHECKER_TYPE));
            return;
        }

        var enableProfile = Boolean.parseBoolean(config.getProperty(Config.PROFILER_ENABLE));
        var profiler = Profiler.getInstance();
        int nHist = Integer.parseInt(config.getProperty(Config.WORKLOAD_HISTORY));
        AtomicInteger bugCount = new AtomicInteger();
        if (enableProfile) {
            profiler.startTick("run_total_time");
        }
        Function<Void, Void> runOneShot = f -> {
            for (int i = 1; i <= nHist; i++) {
                // generate history
                log.info("Start workload generation {} of {}", i, nHist);
                var history = new GeneralGenerator(config).generate();

                // collect result
                log.info("Start history collection");
                try {
                    var collectorInstance = collector.getDeclaredConstructor(Properties.class).newInstance(config);
                    history = collectorInstance.collect(history);
                    collectorInstance.close();
                } catch (InstantiationException | InvocationTargetException | NoSuchMethodException |
                         IllegalAccessException e) {
                    throw new RuntimeException(e);
                }

                // verify history
                log.info("Start history verification");
                if (enableProfile) {
                    profiler.startTick(checker.getName());
                }
                boolean result;
                try {
                    var checkerInstance = checker.getDeclaredConstructor(Properties.class).newInstance(config);
                    result = checkerInstance.verify(history);
                    if (enableProfile) {
                        profiler.endTick(checker.getName());
                        System.gc();
                    }
                    if (!result) {
                        log.info("FIND BUG");
                        // serialize history and output dotfile
                        var bugDir = Paths.get(Config.DEFAULT_CURRENT_PATH, String.format("bug_%d", bugCount.getAndIncrement()));
                        try {
                            Files.createDirectory(bugDir);
                        } catch (IOException e) {

                        }
                        new TextHistorySerializer().serializeHistory(history, Paths.get(bugDir.toString(), "bug_hist.txt").toString());
                        checkerInstance.outputDotFile(Paths.get(bugDir.toString(), "conflict.dot").toString());
                    }
                } catch (InstantiationException | IllegalAccessException | InvocationTargetException |
                         NoSuchMethodException e) {
                    throw new RuntimeException(e);
                }
            }
            return null;
        };

        var variable = config.getProperty(Config.WORKLOAD_VARIABLE);
        if (variable != null && !variable.isBlank()) {
            Profiler.createCSV(variable);
            variable = "workload." + variable;
            var valueListString = config.getProperty(variable);
            var valueList = ConfigParser.parseListString(valueListString);
            for (var value : valueList) {
                config.setProperty(variable, value);
                log.info("Run one shot {} = {}", variable, value);
                runOneShot.apply(null);
                var avgTime = profiler.getAvgTime(checker.getName());
                var maxMemory = profiler.getMemory(checker.getName());
                Profiler.appendToCSV(value, avgTime, maxMemory);
                profiler.removeTag(checker.getName());
            }
        } else {
            runOneShot.apply(null);
        }

        if (enableProfile) {
            profiler.endTick("run_total_time");
        }

        // collect runtime data
        if (enableProfile) {
            for (var tag : profiler.getTags()) {
                log.info("Tag {} run {} times", tag, profiler.getCounter(tag));
                log.info("Total time: {}ms", profiler.getTotalTime(tag));
                log.info("Avg time: {}ms", profiler.getAvgTime(tag));
                log.info("Max memory: {}B", profiler.getMemory(tag));
            }
        }

        // output to the specific dir
        var outputPath = config.getProperty(Config.OUTPUT_PATH, Config.DEFAULT_OUTPUT_PATH);
        RuntimeDataSerializer.getInstance(outputPath).outputToPath(nHist, bugCount.get(), config, enableProfile);
    }

    @SneakyThrows
    public static void main(String... args) {
        int exitCode = new CommandLine(new Main()).execute(args);
        FileUtils.deleteDirectory(Paths.get(Config.DEFAULT_CURRENT_PATH).toFile());
        System.exit(exitCode);
    }
}