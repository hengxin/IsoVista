package checker.VeriStrong_SER;

import com.sun.jna.Library;
import com.sun.jna.Native;

public interface LibSMTCheckerSER extends Library {

    LibSMTCheckerSER INSTANCE = Native.load("smtchecker-ser", LibSMTCheckerSER.class);

    boolean verify(String filepath, String logLevel, boolean pruning, String solverType, String historyType, boolean perf, String perfPath);
}