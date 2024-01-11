package util;

import com.fasterxml.jackson.databind.ObjectMapper;
import config.Config;
import lombok.Data;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

@Slf4j
@Data
public class RuntimeDataSerializer {
    static RuntimeDataSerializer instance;

    private final Path outputPath;
    private final long timestamp;


    @SneakyThrows
    public static synchronized RuntimeDataSerializer getInstance(String dirPath) {
        if (instance == null) {
            var currentTimestamp = System.currentTimeMillis();
            var subDirPath = String.format("run_%d", currentTimestamp);
            var outputPath = Paths.get(dirPath, subDirPath);

            Files.createDirectories(outputPath);
            log.info("Successfully created dir {}", outputPath);

            instance = new RuntimeDataSerializer(outputPath, currentTimestamp);
        }
        return instance;
    }


    @SneakyThrows
    public void outputToPath(int historyCount, int bugCount, Properties config, boolean enableProfiler) {
        // move all files in current dir
        var sourceDirectory = Paths.get(Config.DEFAULT_CURRENT_PATH);
        Files.walk(sourceDirectory)
                .filter(Files::isRegularFile)
                .forEach(sourceFile -> {
                    Path targetFile = outputPath.resolve(sourceDirectory.relativize(sourceFile));
                    try {
                        Files.createDirectories(targetFile.getParent());
                        Files.move(sourceFile, targetFile);
                    } catch (IOException e) {
                        System.err.println("Failed to copy file: " + e.getMessage());
                    }
                });

        // serialize config file
        var configOutputStream = new FileOutputStream(Paths.get(outputPath.toString(), "config.properties").toString());
        config.store(configOutputStream, "config properties");

        // serialize metadata
        Map<String, Object> outputMap = new HashMap<>();
        var profiler = Profiler.getInstance();
        outputMap.put("have_bug", bugCount > 0);
        outputMap.put("timestamp", timestamp);
        outputMap.put("history_count", historyCount);
        outputMap.put("bug_count", bugCount);
        outputMap.put("db_type", config.get(Config.DB_TYPE));
        outputMap.put("db_isolation", config.get(Config.DB_ISOLATION));
        outputMap.put("checker_type", config.get(Config.CHECKER_TYPE));
        outputMap.put("checker_isolation", config.get(Config.CHECKER_ISOLATION));
        if (enableProfiler)
            outputMap.put("run_total_time", profiler.getTotalTime("run_total_time"));

        var objectMapper = new ObjectMapper();
        var json = objectMapper.writeValueAsString(outputMap);

        log.info("{}", json);
        var metadataPath = Paths.get(outputPath.toString(), "metadata.json");
        Files.writeString(metadataPath, json);
    }
}
