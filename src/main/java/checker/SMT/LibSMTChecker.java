package checker.SMT;

import com.sun.jna.Library;
import com.sun.jna.Native;

public interface LibSMTChecker extends Library {
    LibSMTChecker INSTANCE = Native.load("smtchecker", LibSMTChecker.class);

    boolean verify(String filepath, String logLevel, boolean pruning, String solverType, String historyType, boolean outputDot, String dotPath);
}