package collector;

import config.Config;
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
    protected IsolationLevel isolation;

    @SneakyThrows
    public Collector(Properties config) {
        this.url = config.getProperty(Config.DB_URL);
        this.username = config.getProperty(Config.DB_USERNAME);
        this.password = config.getProperty(Config.DB_PASSWORD);
        this.connection = DriverManager.getConnection(url, username, password);
        this.nKey = Long.parseLong(config.getProperty(Config.WORKLOAD_KEY));
        this.isolation = IsolationLevel.valueOf(config.getProperty(Config.DB_ISOLATION));
    }

    public abstract History<KeyType, ValType> collect(History<KeyType, ValType> history);

    protected abstract void createTable();

    protected abstract void createVariables(long nKey);

    protected abstract void dropDatabase();

    @SneakyThrows
    public void close() {
        if (connection != null) {
            connection.close();
        }
    }
}
