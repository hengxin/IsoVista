package util;

import exceptions.NotImplementedException;
import history.serializer.HistorySerializer;
import history.serializer.TextHistorySerializer;

public class HistorySerializerFactory {
    public static HistorySerializer getHistorySerializer(String type) {
        switch (type) {
            case "text":
                return new TextHistorySerializer();
            case "elle":
                throw new NotImplementedException();
            default:
                throw new RuntimeException("Unknown history type");
        }
    }
}
