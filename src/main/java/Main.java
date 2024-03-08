import checker.Checker;
import checker.IsolationLevel;
import collector.Collector;
import config.Config;
import generator.general.GeneralGenerator;
import history.History;
import org.apache.commons.lang3.tuple.Pair;
import util.HistoryLoaderFactory;
import history.serializer.TextHistorySerializer;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.reflections.Reflections;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;
import util.*;

import java.io.FileInputStream;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;

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
        RuntimeInfoRecorder.start();

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

        var isolationStr = config.getProperty(Config.CHECKER_ISOLATION);
        List<Pair<Class<? extends Checker>, IsolationLevel>> checkerIsoList = new ArrayList<>();
        if (isolationStr.startsWith("[")) {
            var isolationList = ConfigParser.parseListString(isolationStr);
            for (var isolation : isolationList) {
                var checkerName = ConfigParser.IsolationToCheckerName(isolation);
                if (checkerName == null) {
                    log.error("Can not find checker of {}", isolation);
                    return;
                }
                checkerIsoList.add(Pair.of(checkers.get(checkerName.toLowerCase()), IsolationLevel.valueOf(isolation.toUpperCase())));
            }
        } else {
            var checkerName = ConfigParser.IsolationToCheckerName(isolationStr);
            if (checkerName == null) {
                log.error("Can not find checker of {}", isolationStr);
                return;
            }
            checkerIsoList.add(Pair.of(checkers.get(checkerName.toLowerCase()), IsolationLevel.valueOf(isolationStr.toUpperCase())));
        }

        // TODO: remove ENABLE_PROFILER
        var enableProfile = Boolean.parseBoolean(config.getProperty(Config.PROFILER_ENABLE));
        var profiler = Profiler.getInstance();
        var skipGeneration = Boolean.parseBoolean(config.getProperty(Config.WORKLOAD_SKIP_GENERATION));
        int historyNum = 1;
        if (!skipGeneration) {
            historyNum = Integer.parseInt(config.getProperty(Config.WORKLOAD_HISTORY));
        }
        int nBatch = 1;
        AtomicInteger bugCount = new AtomicInteger();
        if (enableProfile) {
            profiler.startTick("run_total_time");
        }
        TriFunction<Integer, Integer, Integer, Void> runOneShot = (Integer currentBatch, Integer totalBatch, Integer nHist) -> {
            for (int i = 1; i <= nHist; i++) {
                History history = null;
                if (!skipGeneration) {
                    // generate history
                    log.info("Start workload generation {} of {}", i + nHist * currentBatch, nHist * totalBatch);
                    history = new GeneralGenerator(config).generate();

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
                } else {
                    var historyPath = config.getProperty(Config.HISTORY_PATH).toLowerCase();
                    var historyType = config.getProperty(Config.HISTORY_TYPE).toLowerCase();
                    history = HistoryLoaderFactory.getHistoryLoader(historyType).loadHistory(historyPath);
                }

                // verify history
                for (var checkerAndIsolation : checkerIsoList) {
                    var checker = checkerAndIsolation.getLeft();
                    var isolation = checkerAndIsolation.getRight();
                    config.setProperty(Config.CHECKER_ISOLATION, isolation.toString());
                    log.info("Start history verification using checker {}", checker.getName() + "-" + isolation);
                    if (enableProfile) {
                        profiler.startTick(checker.getName() + "-" + isolation);
                    }
                    boolean result;
                    try {
                        // TODO: ww edge in elle history?
                        var checkerInstance = checker.getDeclaredConstructor(Properties.class).newInstance(config);
                        result = checkerInstance.verify(history);
                        if (enableProfile) {
                            profiler.endTick(checker.getName() + "-" + isolation);
                            System.gc();
                        }
                        if (!result) {
                            log.info("FIND BUG!");
                            // serialize history and output dotfile
                            var bugDir = Paths.get(Config.DEFAULT_CURRENT_PATH, String.format("bug_%d", bugCount.getAndIncrement()));
                            try {
                                Files.createDirectory(bugDir);
                            } catch (IOException e) {

                            }
                            if (!skipGeneration) {
                                new TextHistorySerializer().serializeHistory(history, Paths.get(bugDir.toString(), "bug_hist.txt").toString());
                            }
                            checkerInstance.outputDotFile(Paths.get(bugDir.toString(), "conflict.dot").toString());
                        } else {
                            log.info("NO BUG");
                        }
                    } catch (InstantiationException | IllegalAccessException | InvocationTargetException |
                             NoSuchMethodException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
            return null;
        };

        var variable = config.getProperty(Config.WORKLOAD_VARIABLE);
        if (variable != null && !variable.isBlank()) {
            Profiler.createCSV(variable, checkerIsoList);
            variable = "workload." + variable;
            var valueListString = config.getProperty(variable);
            var valueList = ConfigParser.parseListString(valueListString);
            nBatch = valueList.length;
            for (int i = 0; i < valueList.length; i++) {
                var value = valueList[i];
                config.setProperty(variable, value);
                log.info("Run one shot {} = {}", variable, value);
                runOneShot.apply(i, nBatch, historyNum);
                for (var pair : checkerIsoList) {
                    var checkerIsolation = pair.getLeft().getName() + "-" + pair.getRight();
                    var avgTime = profiler.getAvgTime(checkerIsolation);
                    var maxMemory = profiler.getMemory(checkerIsolation);
                    Profiler.appendToCSV(value, avgTime, maxMemory, pair);
                    profiler.removeTag(checkerIsolation);
                }
            }
        } else {
            runOneShot.apply(0, nBatch, historyNum);
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

        RuntimeInfoRecorder.stop();

        // output to the specific dir
        var outputPath = config.getProperty(Config.OUTPUT_PATH, Config.DEFAULT_OUTPUT_PATH);
        RuntimeDataSerializer.getInstance(outputPath).outputToPath(historyNum * nBatch, bugCount.get(), config, enableProfile);
    }

    @SneakyThrows
    public static void main(String... args) {
        int exitCode = new CommandLine(new Main()).execute(args);
        FileUtils.deleteDirectory(Paths.get(Config.DEFAULT_CURRENT_PATH).toFile());
        System.exit(exitCode);
    }
}