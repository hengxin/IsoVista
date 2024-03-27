package checker.SMT_SI;

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
import java.nio.file.Paths;
import java.util.Map;
import java.util.Properties;

public class SMTCheckerSI<VarType, ValType> implements Checker<VarType, ValType> {
    public static final String NAME = "SMT_SI";
    private static final String DEFAULT_PERF_PATH = "./perf.json";
    private static final String TMP_HIST_PATH = "./tmp_hist.txt";

    static {
        System.loadLibrary("monosat");
    }

    private final Properties config;

    private History<VarType, ValType> history;
    private Transaction<VarType, ValType> satisfyINT;

    public SMTCheckerSI(Properties config) {
        this.config = config;
    }

    @Override
    @SneakyThrows
    public boolean verify(History<VarType, ValType> history) {
        this.history = history;
        if (config.getProperty(Config.HISTORY_TYPE, Config.DEFAULT_HISTORY_TYPE).equals("elle")) {
            history.addInitSessionElle();
        } else {
            history.addInitSession();
        }
        satisfyINT = Utils.verifyInternalConsistency(history);
        if (satisfyINT != null) {
            return false;
        }
        history.removeInitSession();

        if (config.getProperty(Config.HISTORY_TYPE, Config.DEFAULT_HISTORY_TYPE).equals("elle")) {
            new ElleTextHistorySerializer().serializeHistory((History<Integer, ElleHistoryLoader.ElleValue>) history, TMP_HIST_PATH);
        } else {
            new TextHistorySerializer().serializeHistory((History<Long, Long>) history, TMP_HIST_PATH);
        }

        // if using acyclic-minisat(fastest), the shared lib would corrupt
        var accept = LibSMTCheckerSI.INSTANCE.verify(TMP_HIST_PATH, "info", true, "z3", "text", true, DEFAULT_PERF_PATH);

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
        AnomalyInterpreter.interpretSER(this.history);
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
