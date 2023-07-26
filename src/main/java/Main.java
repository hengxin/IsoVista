import checker.C4.C4;
import generator.Generator;
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
        log.info("Start history generation");
        Generator<Long, Long> generator = new GeneralGenerator(config);
        var history = generator.generate();

        // verify history
        log.info("Start history verification");
        var checker = new C4();
        checker.verify(history);

        // collect runtime data

    }

    public static void main(String... args) {
        int exitCode = new CommandLine(new Main()).execute(args);
        System.exit(exitCode);
    }
}
