package util;

import config.Config;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.FileWriter;
import java.nio.file.Paths;

@Slf4j
public class RuntimeStageRecorder {
    static final String ExportCSVPath = Paths.get(Config.DEFAULT_CURRENT_PATH, "runtime_stage.csv").toString();
    static FileWriter csvWriter;
    static String prevStage;

    @SneakyThrows
    public static void createCSV() {
        log.info("Create csv file: {}", ExportCSVPath);
        File file = new File(ExportCSVPath);
        file.createNewFile();
        csvWriter = new FileWriter(file, true);
        csvWriter.write("timestamp,stage\n");
        csvWriter.flush();
    }

    @SneakyThrows
    public synchronized static void updateStage(String stage) {
        if (csvWriter == null) {
            createCSV();
        }
        if (stage.equals(prevStage)) {
            return;
        }
        var currentTimestamp = System.currentTimeMillis();
        csvWriter.write(currentTimestamp + "," + stage + "\n");
        csvWriter.flush();
        RuntimeStageRecorder.prevStage = stage;
    }

    @SneakyThrows
    public static void close() {
        csvWriter.close();
    }
}
