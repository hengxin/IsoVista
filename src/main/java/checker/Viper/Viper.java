package checker.Viper;


import checker.Checker;
import com.fasterxml.jackson.databind.ObjectMapper;
import config.Config;
import exceptions.NotImplementedException;
import history.History;
import history.serializer.ViperHistorySerializer;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FilenameUtils;
import org.yaml.snakeyaml.Yaml;

import java.io.BufferedReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

@Slf4j
public class Viper<VarType, ValType> implements Checker<VarType, ValType> {

    public static final String NAME = "Viper";
    private static final String TMP_DIR_PATH = "./viper_tmp";
    private static final String TMP_HIST_PATH = "./viper_tmp/logs/json";
    private static final String PERF_PATH = "./viper_tmp/test_perf.txt";
    private static final String PYTHON_INTERPRETER = "python3";
    private static final String PYTHON_MAIN_SCRIPT = "./resource/Viper/src/main_allcases.py";
    private static final String CONFIG_PTH = "./viper_tmp/config.yaml";
    private static final Map<String, Object> configYamlMap;
    // magic words in perf file
    private static final String TEST_RUN = "test_run";
    private static final String SATISFY = "sat";
    private static final String CONSTRUCTION = "constructing_graph";
    private static final String SOLVING = "solving";
    private static final String ENCODING = "encoding";

    static {
        try {
            Files.createDirectories(Path.of(TMP_DIR_PATH));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        configYamlMap = new HashMap<>() {{
            put("PREFIX", TMP_DIR_PATH);
            put("LOG_DIR", FilenameUtils.concat(TMP_DIR_PATH, "logs"));
            put("GRAPH_DIR", FilenameUtils.concat(TMP_DIR_PATH, "graphs"));
            put("ANALYSIS_DIR", FilenameUtils.concat(TMP_DIR_PATH, "Analysis_logs"));
            put("DEBUG", false);
            put("REGENERATE", true);
            put("HASFINAL", false);
            put("ALGO", 6);
        }};
        Yaml yaml = new Yaml();
        try (FileWriter writer = new FileWriter(CONFIG_PTH)) {
            yaml.dump(configYamlMap, writer);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private final Properties config;
    private Map<String, Object> result;

    public Viper(Properties config) {
        this.config = config;
    }

    @SneakyThrows
    private void runPython() {
        // python3 ./main_allcases.py --config_file config.yaml --sub_dir json --perf_file ./test_perf.txt --strong-session --exp_name test_run
        ProcessBuilder pb = new ProcessBuilder(PYTHON_INTERPRETER, PYTHON_MAIN_SCRIPT, "--config_file", CONFIG_PTH,
                "--sub_dir", "json", "--perf_file", PERF_PATH, "--strong-session", "--exp_name", TEST_RUN);

        Process process = pb.start();

        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        String line;
        while ((line = reader.readLine()) != null) {
            log.info(line);
        }

        int exitCode = process.waitFor();
        log.info("Python script exited with code: " + exitCode);
    }

    @SneakyThrows
    private void getResult() {
        Optional<String> lastLine = Files.lines(Path.of(PERF_PATH))
                .reduce((first, second) -> second);
        if (lastLine.isEmpty()) {
            log.warn("Last line not found in " + PERF_PATH);
            return;
        }
        ObjectMapper mapper = new ObjectMapper();
        result = (Map<String, Object>) mapper.readValue(lastLine.get(), Map.class).get(TEST_RUN);
    }


    @Override
    @SneakyThrows
    public boolean verify(History<VarType, ValType> history) {
        if (config.getProperty(Config.HISTORY_TYPE, Config.DEFAULT_HISTORY_TYPE).equals("elle")) {
            throw new NotImplementedException();
        } else {
            new ViperHistorySerializer().serializeHistory((History<Long, Long>) history, TMP_HIST_PATH);
        }
        runPython();
        getResult();
        return (boolean) result.get(SATISFY);
    }

    @Override
    @SneakyThrows
    public void outputDotFile(String path) {
        log.warn("Not implemented outputDotFile in Viper");
    }

    @Override
    public Map<String, Long> getProfileInfo() {
        return new HashMap<>() {{
            put("Construction", (long) ((double) result.get(CONSTRUCTION) * 1000));
            put("Encoding", (long) ((double) result.get(ENCODING) * 1000));
            put("Solving", (long) ((double) result.get(SOLVING) * 1000));
        }};
    }
}
