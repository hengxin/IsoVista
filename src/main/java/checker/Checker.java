package checker;

import history.History;

public interface Checker<KeyType, ValType> {
    /**
     * Verify a history.
     *
     * @param history the history to verify
     * @return true if the history is valid, false otherwise
     */
    boolean verify(History<KeyType, ValType> history);

    /**
     * Outputs a dot file to the given path.
     *
     * @param  path  the path where the dot file will be outputted
     */
    void outputDotFile(String path);
}
