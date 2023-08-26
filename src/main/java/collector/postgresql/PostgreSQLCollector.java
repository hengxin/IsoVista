package collector.postgresql;

import collector.Collector;
import history.History;
import lombok.SneakyThrows;

import java.sql.Statement;
import java.util.ArrayList;
import java.util.Properties;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import java.sql.ResultSet;

public class PostgreSQLCollector extends Collector<Long, Long> {

    @SneakyThrows
    public PostgreSQLCollector(Properties config) {
        super(config);
    }

    @Override
    @SneakyThrows
    public History<Long, Long> collect(History<Long, Long> history) {
        createTable();
        createVariables(nKey);
        ExecutorService executor = Executors.newFixedThreadPool(history.getSessions().size());
        var todo = new ArrayList<Callable<Void>>();
        history.getSessions().values().forEach(session -> {
            Callable<Void> task = () -> {
                var node = new PostgreSQLClient(url, username, password);
                node.execSession(session);
                return null;
            };
            todo.add(task);
        });
        executor.invokeAll(todo);
        dropDatabase();
        return history;
    }

    @Override
    @SneakyThrows
    protected void createTable() {
        var statement = connection.createStatement();
        // create database if not exists
        ResultSet rs = statement.executeQuery("SELECT schema_name FROM information_schema.schemata WHERE schema_name = 'dbtest'");
        if (!rs.next()) {
            statement.executeUpdate("CREATE SCHEMA dbtest ");
        }
        statement.executeUpdate("DROP TABLE IF EXISTS dbtest.variables");
        statement.executeUpdate("CREATE TABLE IF NOT EXISTS dbtest.variables (var BIGINT PRIMARY KEY, val BIGINT NOT NULL)");
    }

    @Override
    @SneakyThrows
    protected void createVariables(long nKey) {
        var insertStmt = connection.prepareStatement("INSERT INTO dbtest.variables (var, val) values (?, 0)");
        for (long k = 1; k <= nKey; k++) {
            insertStmt.setLong(1, k);
            insertStmt.addBatch();
        }
        insertStmt.executeBatch();
    }

    @Override
    @SneakyThrows
    protected void dropDatabase() {
        Statement statement = connection.createStatement();
        statement.executeUpdate("DROP SCHEMA dbtest CASCADE");
    }
}
