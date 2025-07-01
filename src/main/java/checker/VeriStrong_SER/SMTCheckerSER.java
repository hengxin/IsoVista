package checker.VeriStrong_SER;

import checker.Checker;
import checker.PolySI.verifier.Utils;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import config.Config;
import history.History;
import history.Transaction;
import history.loader.ElleHistoryLoader;
import history.serializer.ElleTextHistorySerializer;
import history.serializer.TextHistorySerializer;
import lombok.SneakyThrows;
import org.apache.commons.io.FileUtils;
import util.AnomalyInterpreter;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import java.util.Properties;

public class SMTCheckerSER<VarType, ValType> implements Checker<VarType, ValType> {
    public static final String NAME = "SMT_SER";
    private static final String DEFAULT_PERF_PATH = "./perf.json";
    private static final String TMP_HIST_PATH = "./tmp_hist.txt";

    static {
        System.loadLibrary("monosat");
    }

    private final Properties config;

    private History<VarType, ValType> history;
    private Transaction<VarType, ValType> satisfyINT;

    public SMTCheckerSER(Properties config) {
        this.config = config;
    }

    @Override
    @SneakyThrows
    public boolean verify(History<VarType, ValType> history) {
        this.history = history;
        String historyType = config.getProperty(Config.HISTORY_TYPE, Config.DEFAULT_HISTORY_TYPE);
        if (historyType.equals("elle")) {
            history.addInitSessionElle();
            new ElleTextHistorySerializer().serializeHistory((History<Integer, ElleHistoryLoader.ElleValue>) history, TMP_HIST_PATH);
        } else {
            new TextHistorySerializer().serializeHistory((History<Long, Long>) history, TMP_HIST_PATH);
        }

        satisfyINT = Utils.verifyInternalConsistency(history);
        if (satisfyINT != null) {
            return false;
        }

        var accept = LibSMTCheckerSER.INSTANCE.verify(TMP_HIST_PATH, "info", true, "acyclic-minisat", historyType, true, DEFAULT_PERF_PATH);

        FileUtils.delete(new File(TMP_HIST_PATH));
        return accept;
    }

    @Override
    @SneakyThrows
    public void outputDotFile(String path) {
        if (satisfyINT != null) {
            Utils.intConflictToDot(satisfyINT);
            return;
        }
        if (this.history == null) {
            return;
        }
        var dotOutputStr = AnomalyInterpreter.interpretSER(this.history);
        Files.writeString(Path.of(path), dotOutputStr, StandardOpenOption.CREATE);
    }

    @Override
    @SneakyThrows
    public Map<String, Long> getProfileInfo() {
        var jsonPath = Paths.get(DEFAULT_PERF_PATH);
        if (!Files.exists(jsonPath)) {
            return null;
        }
        String jsonContent = Files.readString(jsonPath);
        ObjectMapper objectMapper = new ObjectMapper();
        return objectMapper.readValue(jsonContent, new TypeReference<Map<String, Long>>() {
        });
    }
}
