package history.serializer;

import history.History;

public interface HistorySerializer<KeyType, ValType> {
    void serializeHistory(History<KeyType, ValType> history, String path);
}
