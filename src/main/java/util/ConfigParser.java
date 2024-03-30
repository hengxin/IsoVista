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

    static Map<String, String> IsolationAbbreviation = new HashMap<>();

    static {
        ConfigToCheckerIsolationMap.put("READ_COMMITTED", Pair.of("C4", IsolationLevel.READ_COMMITTED));
        ConfigToCheckerIsolationMap.put("REPEATABLE_READ", Pair.of("C4", IsolationLevel.REPEATABLE_READ));
        ConfigToCheckerIsolationMap.put("READ_ATOMICITY", Pair.of("C4", IsolationLevel.READ_ATOMICITY));
        ConfigToCheckerIsolationMap.put("TRANSACTIONAL_CAUSAL_CONSISTENCY", Pair.of("C4", IsolationLevel.CAUSAL_CONSISTENCY));
        ConfigToCheckerIsolationMap.put("SNAPSHOT_ISOLATION", Pair.of("PolySI", IsolationLevel.SNAPSHOT_ISOLATION));
        ConfigToCheckerIsolationMap.put("SERIALIZABLE", Pair.of("SMT_SER", IsolationLevel.SERIALIZABLE));
        ConfigToCheckerIsolationMap.put("VIPER_SNAPSHOT_ISOLATION", Pair.of("Viper", IsolationLevel.SNAPSHOT_ISOLATION));
        ConfigToCheckerIsolationMap.put("POLYSI+_SNAPSHOT_ISOLATION", Pair.of("SMT_SI", IsolationLevel.SNAPSHOT_ISOLATION));

        IsolationAbbreviation.put("READ_COMMITTED", "RC");
        IsolationAbbreviation.put("REPEATABLE_READ", "RR");
        IsolationAbbreviation.put("READ_ATOMICITY", "RA");
        IsolationAbbreviation.put("TRANSACTIONAL_CAUSAL_CONSISTENCY", "TCC");
        IsolationAbbreviation.put("SNAPSHOT_ISOLATION", "SI");
        IsolationAbbreviation.put("SERIALIZABLE", "SER");
        IsolationAbbreviation.put("VIPER_SNAPSHOT_ISOLATION", "Viper");
        IsolationAbbreviation.put("POLYSI+_SNAPSHOT_ISOLATION", "PolySI+");
    }

    public static Pair<String, IsolationLevel> parseIsolationConfig(String isolationConfig) {
        if (!ConfigToCheckerIsolationMap.containsKey(isolationConfig)) {
            throw new RuntimeException("Unknown isolation config: " + isolationConfig);
        }
        return ConfigToCheckerIsolationMap.get(isolationConfig);
    }

    public static String getCheckerIsolationAbbreviation(String checkerIsolation) {
        if (!IsolationAbbreviation.containsKey(checkerIsolation)) {
            return checkerIsolation;
        }
        return IsolationAbbreviation.get(checkerIsolation);
    }
}