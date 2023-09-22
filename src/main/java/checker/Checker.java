package checker;

import history.History;

public interface Checker<KeyType, ValType> {
    /**
     * Verify a history.
     * @param history
     * @return true if valid, false otherwise
     */
    boolean verify(History<KeyType, ValType> history);
}
