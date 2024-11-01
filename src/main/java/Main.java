import checker.Checker;
import checker.IsolationLevel;
import collector.Collector;
import config.Config;
import generator.general.GeneralGenerator;
import history.History;
import history.serializer.TextHistorySerializer;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.tuple.Triple;
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
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

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
        RuntimeStageRecorder.createCSV();

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
        List<Triple<Class<? extends Checker>, IsolationLevel, String>> checkerIsoList = new ArrayList<>();
        if (isolationStr.startsWith("[")) {
            var isolationList = ConfigParser.parseListString(isolationStr);
            for (var isolation : isolationList) {
                var checkerIso = ConfigParser.parseIsolationConfig(isolation);
                checkerIsoList.add(Triple.of(checkers.get(checkerIso.getKey().toLowerCase()), checkerIso.getRight(), isolation));
            }
        } else {
            var checkerIso = ConfigParser.parseIsolationConfig(isolationStr);
            checkerIsoList.add(Triple.of(checkers.get(checkerIso.getKey().toLowerCase()), checkerIso.getRight(), isolationStr));
        }

        // TODO: remove ENABLE_PROFILER
        var enableProfile = Boolean.parseBoolean(config.getProperty(Config.PROFILER_ENABLE));
        var profiler = Profiler.getInstance();
        var skipGeneration = Boolean.parseBoolean(config.getProperty(Config.WORKLOAD_SKIP_GENERATION));
        int historyNum = 1;
        if (!skipGeneration) {
            historyNum = Integer.parseInt(config.getProperty(Config.WORKLOAD_HISTORY));
        } else {
            log.info("Skip workload generation");
            // disable some config
            config.setProperty(Config.DB_TYPE, "NONE");
            config.setProperty(Config.DB_ISOLATION, "NONE");
            config.setProperty(Config.WORKLOAD_HISTORY, "1");
            config.setProperty(Config.WORKLOAD_VARIABLE, "");
        }
        var isolationLevel = config.getProperty(Config.CHECKER_ISOLATION);
        int nBatch = 1;
        AtomicInteger bugCount = new AtomicInteger();
        if (enableProfile) {
            profiler.startTick("run_total_time");
        }
        Map<String, Map<String, Long>> profileInfo = new HashMap<>();
        TriFunction<Integer, Integer, Integer, Void> runOneShot = (Integer currentBatch, Integer totalBatch, Integer nHist) -> {
            for (int i = 1; i <= nHist; i++) {
                History history = null;
                if (!skipGeneration) {
                    // generate history
                    log.info("Start workload generation {} of {}", i + nHist * currentBatch, nHist * totalBatch);
                    RuntimeStageRecorder.updateStage("History Collection");
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
                    var isolation = checkerAndIsolation.getMiddle();
                    config.setProperty(Config.CHECKER_ISOLATION, isolation.toString());
                    log.info("Start history verification using checker {}", checker.getName() + "-" + isolation);
                    String tag = checker.getName() + "-" + isolation;
                    if (enableProfile) {
                        profiler.startTick(tag);
                    }
                    RuntimeStageRecorder.updateStage(ConfigParser.getCheckerIsolationAbbreviation(checkerAndIsolation.getRight()) + " Verification");
                    boolean result;
                    try {
                        var checkerInstance = checker.getDeclaredConstructor(Properties.class).newInstance(config);
                        result = checkerInstance.verify(history);
                        if (enableProfile) {
                            profiler.endTick(tag);
                            if (checkerInstance.getProfileInfo() != null) {
                                profileInfo.put(tag, checkerInstance.getProfileInfo());
                            }
                            profiler.resetMaxMemory();
                            System.gc();
                        }
                        if (!result) {
                            log.info("FIND BUG!");
                            // serialize history and output dotfile
                            var bugDir = Paths.get(Config.DEFAULT_CURRENT_PATH, String.format("bug_%d", bugCount.getAndIncrement()));
                            try {
                                Files.createDirectory(bugDir);
                            } catch (IOException e) {
                                // do nothing
                            }
                            if (config.getOrDefault(Config.HISTORY_TYPE, Config.DEFAULT_HISTORY_TYPE) == Config.DEFAULT_HISTORY_TYPE) {
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
        BiConsumer<String, String> outputProfileInfo = (String var, String val) -> {
            var stages = profileInfo.values().stream()
                    .map(Map::keySet)
                    .flatMap(Set::stream)
                    .distinct()
                    .collect(Collectors.toList());
            Profiler.createCSV(var, checkerIsoList.stream().map(Triple::getRight).collect(Collectors.toList()), stages);
            for (var triple : checkerIsoList) {
                var checkerIsolation = triple.getLeft().getName() + "-" + triple.getMiddle();
                var avgTime = profiler.getAvgTime(checkerIsolation);
                var maxMemory = profiler.getMemory(checkerIsolation);
                var profileMap = profileInfo.getOrDefault(checkerIsolation, new HashMap<String, Long>());
                var stageTimeList = new ArrayList<Long>();
                stages.forEach(stage -> stageTimeList.add(profileMap.getOrDefault(stage, 0L)));
                Profiler.appendToCSV(val, Math.min(avgTime, stageTimeList.stream().reduce(0L, Long::sum)), maxMemory, triple.getRight(), stageTimeList);
                profiler.removeTag(checkerIsolation);
            }
        };
        if (variable != null && !variable.isBlank()) {
            var fullVariable = "workload." + variable;
            var valueListString = config.getProperty(fullVariable);
            var valueList = ConfigParser.parseListString(valueListString);
            nBatch = valueList.length;
            for (int i = 0; i < valueList.length; i++) {
                var value = valueList[i];
                config.setProperty(fullVariable, value);
                log.info("Run one shot {} = {}", fullVariable, value);
                runOneShot.apply(i, nBatch, historyNum);
                outputProfileInfo.accept(variable, value);
            }
        } else {
            runOneShot.apply(0, nBatch, historyNum);
            outputProfileInfo.accept(null, "0");
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
        RuntimeStageRecorder.close();

        // output to the specific dir
        var outputPath = config.getProperty(Config.OUTPUT_PATH, Config.DEFAULT_OUTPUT_PATH);
        config.setProperty(Config.CHECKER_ISOLATION, isolationLevel);
        RuntimeDataSerializer.getInstance(outputPath).outputToPath(historyNum * nBatch, bugCount.get(), config, enableProfile);
    }

    @SneakyThrows
    public static void main(String... args) {
        int exitCode = new CommandLine(new Main()).execute(args);
        FileUtils.deleteDirectory(Paths.get(Config.DEFAULT_CURRENT_PATH).toFile());
        System.exit(exitCode);
    }
}