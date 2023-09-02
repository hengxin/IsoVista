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

import java.io.FileInputStream;
import java.util.Properties;
import java.util.concurrent.Callable;

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

            boolean useMySQL = false;
//            history = new MySQLCollector(config).collect(history);
//            history = new PostgreSQLCollector(config).collect(history);
            history = new H2Collector(config).collect(history);

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

    }

    public static void main(String... args) {
        int exitCode = new CommandLine(new Main()).execute(args);
        System.exit(exitCode);
    }
}
