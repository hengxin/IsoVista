import checker.C4.C4;
import collector.H2.H2Client;
import collector.H2.H2Collector;
import collector.mysql.MySQLCollector;
import collector.postgresql.PostgreSQLClient;
import collector.postgresql.PostgreSQLCollector;
import generator.general.GeneralGenerator;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;
import util.Profiler;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.util.*;
import java.util.concurrent.Callable;

import org.apache.commons.lang3.tuple.Pair;


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

    public void run(Properties config) {
        // generate history
        int nHist = Integer.parseInt(config.getProperty("workload.history"));
        for (int i = 1; i <= nHist; i++) {
            log.info("Start history generation {} of {}", i, nHist);
            var history = new GeneralGenerator(config).generate();

            // collect result
            log.info("Start history collection");

            history = new MySQLCollector(config).collect(history);
//            history = new PostgreSQLCollector(config).collect(history);
//            history = new H2Collector(config).collect(history);

            // verify history
            log.info("Start history verification");
            boolean result = new C4<Long, Long>().verify(history);
            if (result) {
                log.info("find bug");
            } else {
                log.info("No bugs found");
            }
        }

        // collect runtime data
        Profiler profiler = Profiler.getInstance();
        exportToCSV(profiler.getDurations(), profiler.getMemories());
    }

    @SneakyThrows
    public void exportToCSV(Collection<Pair<String, Long>> durations, Collection<Pair<String, Long>> memories) {
        String header = "process,duration(ms),memory(MB)\n";
        List<String> lines = new ArrayList<>();
        var memoryIterator = memories.iterator();
        var durationIterator = durations.iterator();

        while (memoryIterator.hasNext() && durationIterator.hasNext()) {
            Pair<String, Long> memory = memoryIterator.next();
            Pair<String, Long> duration = durationIterator.next();

            String process = memory.getKey();
            long memoryValue = memory.getValue();
            long durationValue = duration.getValue();

            String line = process + "," + durationValue + "," + Utils.formatMemory(memoryValue) + "\n";

            lines.add(line);
        }

        // output lines to a csv file
        File file = new File("./result/runtime.csv");
        FileWriter csvWriter = new FileWriter(file);
        csvWriter.write(header);
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