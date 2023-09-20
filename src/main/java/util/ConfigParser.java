package util;

public class ConfigParser {
    public static String[] parseListString(String input) {
        if (!input.startsWith("[") || !input.endsWith("]")) {
            throw new RuntimeException("List string format error");
        }
        return input.substring(1, input.length() - 1).split(",");
    }
}