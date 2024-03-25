package util;

import checker.IsolationLevel;
import org.apache.commons.lang3.tuple.Pair;

import java.util.HashMap;
import java.util.Map;

public class ConfigParser {
    public static String[] parseListString(String input) {
        if (!input.startsWith("[") || !input.endsWith("]")) {
            throw new RuntimeException("List string format error");
        }
        input = input.replace("[", "").replace("]", "")
                .replace(" ", "").replace("'", "");
        return input.split(",");
    }

    static Map<String, Pair<String, IsolationLevel>> ConfigToCheckerIsolationMap = new HashMap<>();

    static {
        ConfigToCheckerIsolationMap.put("READ_COMMITTED", Pair.of("C4", IsolationLevel.READ_COMMITTED));
        ConfigToCheckerIsolationMap.put("READ_ATOMICITY", Pair.of("C4", IsolationLevel.READ_ATOMICITY));
        ConfigToCheckerIsolationMap.put("CAUSAL_CONSISTENCY", Pair.of("C4", IsolationLevel.CAUSAL_CONSISTENCY));
        ConfigToCheckerIsolationMap.put("SNAPSHOT_ISOLATION", Pair.of("PolySI", IsolationLevel.SNAPSHOT_ISOLATION));
        ConfigToCheckerIsolationMap.put("SERIALIZABLE", Pair.of("SMT", IsolationLevel.SERIALIZABLE));
        ConfigToCheckerIsolationMap.put("VIPER_SNAPSHOT_ISOLATION", Pair.of("Viper", IsolationLevel.SNAPSHOT_ISOLATION));
        ConfigToCheckerIsolationMap.put("CUSTOM_SNAPSHOT_ISOLATION", Pair.of("SMT_SI", IsolationLevel.SNAPSHOT_ISOLATION));
    }

    public static Pair<String, IsolationLevel> parseIsolationConfig(String isolationConfig) {
        if (!ConfigToCheckerIsolationMap.containsKey(isolationConfig)) {
            throw new RuntimeException("Unknown isolation config: " + isolationConfig);
        }
        return ConfigToCheckerIsolationMap.get(isolationConfig);
    }
}