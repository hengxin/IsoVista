package checker.SMT;

import checker.Checker;
import checker.PolySI.verifier.Utils;
import config.Config;
import history.History;
import history.loader.ElleHistoryLoader;
import history.serializer.ElleTextHistorySerializer;
import history.serializer.TextHistorySerializer;
import lombok.SneakyThrows;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.FileWriter;
import java.util.Properties;

public class SMTChecker<VarType, ValType> implements Checker<VarType, ValType> {

    public static final String NAME = "SMT";
    private static final String DEFAULT_DOT_PATH = "./conflict.dot";
    private static final String TMP_HIST_PATH = "./tmp_hist.txt";
    private final Properties config;

    public SMTChecker(Properties config) {
        this.config = config;
    }

    @Override
    @SneakyThrows
    public boolean verify(History<VarType, ValType> history) {
        if (config.getProperty(Config.HISTORY_TYPE, Config.DEFAULT_HISTORY_TYPE).equals("elle")) {
            new ElleTextHistorySerializer().serializeHistory((History<Integer, ElleHistoryLoader.ElleValue>) history, TMP_HIST_PATH);
        } else {
            new TextHistorySerializer().serializeHistory((History<Long, Long>) history, TMP_HIST_PATH);
        }

        var satisfy_int = Utils.verifyInternalConsistency(history);
        if (satisfy_int != null) {
            FileWriter writer = new FileWriter(DEFAULT_DOT_PATH);
            writer.write(Utils.intConflictToDot(satisfy_int));
            writer.close();
            return false;
        }

        var accept = LibSMTChecker.INSTANCE.verify(TMP_HIST_PATH, "info", false, "z3", "text", true, DEFAULT_DOT_PATH);
        FileUtils.delete(new File(TMP_HIST_PATH));
        return accept;
    }

    @Override
    @SneakyThrows
    public void outputDotFile(String path) {
        FileUtils.moveFile(new File(DEFAULT_DOT_PATH), new File(path));
    }
}
