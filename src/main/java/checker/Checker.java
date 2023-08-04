package checker;

import history.History;

public interface Checker<KeyType, ValType> {
    boolean verify(History<KeyType, ValType> history);
}
