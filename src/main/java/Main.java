import checker.Checker;
import collector.Collector;
import generator.general.GeneralGenerator;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.reflections.Reflections;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.util.*;
import java.util.concurrent.Callable;

import org.apache.commons.lang3.tuple.Pair;
import util.Profiler;

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

        Profiler profiler = Profiler.getInstance();
        // generate history
        int nHist = Integer.parseInt(config.getProperty("workload.history"));
        for (int i = 1; i <= nHist; i++) {
            log.info("Start history generation {} of {}", i, nHist);
            var history = new GeneralGenerator(config).generate();

            // collect result
            log.info("Start history collection");
            history = collector.getDeclaredConstructor(Properties.class).newInstance(config).collect(history);

            // verify history
            log.info("Start history verification");
            profiler.startTick("checker_performance");
            boolean result = checker.getDeclaredConstructor().newInstance().verify(history);
            profiler.endTick("checker_performance");
            if (result) {
                log.info("find bug");
            }
        }
        // collect runtime data
        String session = config.getProperty("workload.session");
        exportToCSV(session, profiler.getDurations(), profiler.getMaxMemory());

    }

    @SneakyThrows
    public void exportToCSV(String session, Collection<Pair<String, Long>> durations, long maxMemory) {
        List<String> lines = new ArrayList<>();
        for (var p : durations) {
            lines.add(String.format("%s,%s,%s\n", session, p.getValue(), Utils.formatMemory(maxMemory)));
        }

        // output lines to a csv file
        File file = new File("./result/runtime.csv");
        FileWriter csvWriter = new FileWriter(file, true);
        for (String line : lines) {
            csvWriter.write(line);
        }
        csvWriter.close();
    }

    public static void main(String... args) {
        int exitCode = new CommandLine(new Main()).execute(args);
        System.exit(exitCode);
    }
}

class Utils {
    static String formatMemory(Long memoryBytes) {
        double[] scale = { 1, 1024, 1024 * 1024, 1024 * 1024 * 1024 };
        String[] unit = { "B", "KB", "MB", "GB" };

        for (int i = scale.length - 1; i >= 0; i--) {
            if (i == 0 || memoryBytes >= scale[i]) {
                return String.format("%.1f", memoryBytes / scale[i]);
            }
        }
        throw new Error("should not be here");
    }
}