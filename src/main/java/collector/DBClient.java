package collector;

import history.Session;
import lombok.SneakyThrows;

import java.sql.Connection;
import java.sql.DriverManager;

public abstract class DBClient {
    protected String url;
    protected String username;
    protected String password;
    protected Connection connection;
    protected int maxRestartTimes = 1000;

    @SneakyThrows
    public DBClient(String url, String username, String password) {
        this.url = url;
        this.username = username;
        this.password = password;
        this.connection = DriverManager.getConnection(url, username, password);
    }

    public abstract void execSession(Session<Long, Long> session, IsolationLevel isolationLevel);
}
