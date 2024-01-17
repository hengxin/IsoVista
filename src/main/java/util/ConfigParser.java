package util;

import java.util.HashMap;
import java.util.Map;

public class ConfigParser {
    public static String[] parseListString(String input) {
        if (!input.startsWith("[") || !input.endsWith("]")) {
            throw new RuntimeException("List string format error");
        }
        return input.substring(1, input.length() - 1).split(",");
    }

    static Map<String, String> IsolationCheckerMap = new HashMap<>();

    static {
        IsolationCheckerMap.put("READ_COMMITTED", "C4");
        IsolationCheckerMap.put("READ_ATOMICITY", "C4");
        IsolationCheckerMap.put("CAUSAL_CONSISTENCY", "C4");
        IsolationCheckerMap.put("SNAPSHOT_ISOLATION", "PolySI");
        IsolationCheckerMap.put("SERIALIZABLE", "SMT");
    }

    public static String IsolationToCheckerName(String isolation) {
        return IsolationCheckerMap.get(isolation);
    }
}