package config;

public class Config {
    // database configs
    public static final String DB_URL = "db.url";
    public static final String DB_USERNAME = "db.username";
    public static final String DB_PASSWORD = "db.password";
    public static final String DB_ISOLATION = "db.isolation";
    public static final String DB_TYPE = "db.type";

    // workload configs
    public static final String WORKLOAD_TYPE = "workload.type";
    public static final String WORKLOAD_HISTORY = "workload.history";
    public static final String WORKLOAD_SESSION = "workload.session";
    public static final String WORKLOAD_TRANSACTION = "workload.transaction";
    public static final String WORKLOAD_OPERATION = "workload.operation";
    public static final String WORKLOAD_READ_PROPORTION = "workload.readproportion";
    public static final String WORKLOAD_KEY = "workload.key";
    public static final String WORKLOAD_DISTRIBUTION = "workload.distribution";
    public static final String WORKLOAD_VARIABLE = "workload.variable";

    // checker configs
    public static final String CHECKER_TYPE = "checker.type";
    public static final String CHECKER_ISOLATION = "checker.isolation";

    // profiler configs
    public static final String PROFILER_ENABLE = "profiler.enable";
}
