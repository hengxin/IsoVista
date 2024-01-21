package util;

import history.loader.ElleHistoryLoader;
import history.loader.HistoryLoader;
import history.loader.TextHistoryLoader;

public class HistoryLoaderFactory {
    public static HistoryLoader getHistoryLoader(String type) {
        switch (type) {
            case "text":
                return new TextHistoryLoader();
            case "elle":
                return new ElleHistoryLoader();
            default:
                throw new RuntimeException("Unknown history type");
        }
    }
}
