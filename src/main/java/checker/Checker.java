package checker;

import history.History;

public interface Checker {
    boolean verify(History<?, ?> history);
}
