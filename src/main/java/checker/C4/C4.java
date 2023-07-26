package checker.C4;

import checker.Checker;
import history.History;

public class C4 implements Checker {
    @Override
    public boolean verify(History<?, ?> history) {
        return false;
    }
}
