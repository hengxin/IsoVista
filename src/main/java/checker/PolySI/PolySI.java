package checker.PolySI;

import checker.Checker;
import checker.IsolationLevel;
import checker.PolySI.verifier.Pruning;
import checker.PolySI.verifier.SIVerifier;
import config.Config;
import history.History;
import util.Profiler;

import java.util.Properties;

public class PolySI<VarType, ValType> implements Checker<VarType, ValType> {
    private final Boolean noPruning = false;

    private final Boolean noCoalescing = false;

    private final Boolean dotOutput = false;

    private final Profiler profiler = Profiler.getInstance();

    public static final String NAME = "PolySI";
    public static IsolationLevel ISOLATION_LEVEL;

    public PolySI(Properties config) {
        ISOLATION_LEVEL = IsolationLevel.valueOf(config.getProperty(Config.CHECKER_ISOLATION));
    }

    @Override
    public boolean verify(History<VarType, ValType> history) {
        history.addInitSession();
        Pruning.setEnablePruning(!noPruning);
        SIVerifier.setCoalesceConstraints(!noCoalescing);
        SIVerifier.setDotOutput(dotOutput);

        profiler.startTick("ENTIRE_EXPERIMENT");
        var pass = true;
        var verifier = new SIVerifier<>(history);
        pass = verifier.audit();
        profiler.endTick("ENTIRE_EXPERIMENT");

        history.removeInitSession();
        return pass;
    }
}
