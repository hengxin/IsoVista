package history.loader;

import history.History;

public interface HistoryLoader<KeyType, ValType> {
    History<KeyType, ValType> loadHistory(String path);
}
