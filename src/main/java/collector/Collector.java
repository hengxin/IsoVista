package collector;

import history.History;
import lombok.SneakyThrows;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.Properties;

public abstract class Collector<KeyType, ValType> {
    protected String url;
    protected String username;
    protected String password;
    protected Connection connection;
    protected long nKey;
    protected DBType type;

    @SneakyThrows
    public Collector(Properties config) {
        this.url = config.getProperty("db.url");
        this.username = config.getProperty("db.username");
        this.password = config.getProperty("db.password");
        this.connection = DriverManager.getConnection(url, username, password);
        this.nKey = Long.parseLong(config.getProperty("workload.key"));
    }

    public abstract History<KeyType, ValType> collect(History<KeyType, ValType> history);

    protected abstract void createTable();

    protected abstract void createVariables(long nKey);

    protected abstract void dropDatabase();
}
