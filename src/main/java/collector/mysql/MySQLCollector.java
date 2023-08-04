package collector.mysql;

import collector.Collector;
import history.History;
import lombok.SneakyThrows;

import java.sql.Statement;
import java.util.Properties;

public class MySQLCollector extends Collector<Long, Long> {

    @SneakyThrows
    public MySQLCollector(Properties config) {
        super(config);
    }

    @Override
    public History<Long, Long> collect(History<Long, Long> history) {
        createTable();
        createVariables(nKey);
        history.getSessions().values().parallelStream().forEach((session -> {
            var node = new MySQLClient(url, username, password);
            node.execSession(session);
        }));
        dropDatabase();
        return history;
    }

    @Override
    @SneakyThrows
    protected void createTable() {
        var statement = connection.createStatement();
        statement.executeUpdate("CREATE DATABASE IF NOT EXISTS dbtest");
        statement.executeUpdate("DROP TABLE IF EXISTS dbtest.variables");
        statement.executeUpdate("CREATE TABLE IF NOT EXISTS dbtest.variables (var BIGINT(64) UNSIGNED NOT NULL PRIMARY KEY, val BIGINT(64) UNSIGNED NOT NULL)");
    }

    @Override
    @SneakyThrows
    protected void createVariables(long nKey) {
        var insertStmt = connection.prepareStatement("INSERT INTO dbtest.variables (var, val) values (?, 0)");
        for (long k = 0; k < nKey; k++) {
            insertStmt.setLong(1, k);
            insertStmt.addBatch();
        }
        insertStmt.executeBatch();
    }

    @Override
    @SneakyThrows
    protected void dropDatabase() {
        Statement statement = connection.createStatement();
        statement.executeUpdate("DROP DATABASE dbtest");
    }
}
