package checker.SMT_SI;

import com.sun.jna.Library;
import com.sun.jna.Native;

public interface LibSMTCheckerSI extends Library {

    LibSMTCheckerSI INSTANCE = Native.load("smtchecker-si", LibSMTCheckerSI.class);

    boolean verify(String filepath, String logLevel, boolean pruning, String solverType, String historyType, boolean perf, String perfPath);
}