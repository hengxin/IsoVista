import checker.Checker;
import collector.Collector;
import generator.general.GeneralGenerator;
import history.serializer.TextHistorySerializer;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.reflections.Reflections;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;
import util.ConfigParser;
import util.Profiler;

import java.io.FileInputStream;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.Callable;
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

        var collector = collectors.get(config.getProperty("db.type").toLowerCase());
        if (collector == null) {
            log.error("Can not find collector of {}", config.getProperty("db.type"));
            return;
        }

        var checker = checkers.get(config.getProperty("checker.type").toLowerCase());
        if (checker == null) {
            log.error("Can not find checker {}", config.getProperty("checker.type"));
            return;
        }

        var enableProfile = Boolean.parseBoolean(config.getProperty("profiler.enable"));
        Profiler profiler = Profiler.getInstance();
        int nHist = Integer.parseInt(config.getProperty("workload.history"));
        Function<Void, Void> runOneShot = f -> {
            for (int i = 1; i <= nHist; i++) {
                // generate history
                log.info("Start history generation {} of {}", i, nHist);
                var history = new GeneralGenerator(config).generate();

                // collect result
                log.info("Start history collection");
                try {
                    history = collector.getDeclaredConstructor(Properties.class).newInstance(config).collect(history);
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
                    result = checker.getDeclaredConstructor(Properties.class).newInstance(config).verify(history);
                } catch (InstantiationException | IllegalAccessException | InvocationTargetException |
                         NoSuchMethodException e) {
                    throw new RuntimeException(e);
                }
                if (enableProfile) {
                    profiler.endTick(checker.getName());
                    System.gc();
                }
                if (!result) {
                    log.info("find bug");
                    new TextHistorySerializer().serializeHistory(history, "./result/hist.txt");
                }
            }
            return null;
        };

        var variable = config.getProperty("workload.variable");
        if (variable != null) {
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

        // collect runtime data
        if (enableProfile) {
            for (var tag : profiler.getTags()) {
                log.info("Tag {} run {} times", tag, profiler.getCounter(tag));
                log.info("Total time: {}ms", profiler.getTotalTime(tag));
                log.info("Avg time: {}ms", profiler.getAvgTime(tag));
                log.info("Max memory: {}B", profiler.getMemory(tag));
            }
        }
    }

    public static void main(String... args) {
        int exitCode = new CommandLine(new Main()).execute(args);
        System.exit(exitCode);
    }
}