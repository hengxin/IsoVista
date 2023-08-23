package collector.sqlite;

import collector.Collector;
import collector.DBType;
import collector.InMemoryDBCollector;
import history.History;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Properties;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
public class SQLiteCollector extends Collector<Long, Long> implements InMemoryDBCollector {

    @SneakyThrows
    public SQLiteCollector(Properties config) {
        super(config);
        this.type = DBType.SQLITE;
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
                var node = new SQLiteClient(url, username, password);
                node.execSession(session);
                return null;
            };
            todo.add(task);
        });
        executor.invokeAll(todo);
        dropDatabase();
        shutdown();
        return history;
    }

    @Override
    @SneakyThrows
    protected void createTable() {
        var statement = connection.createStatement();
        statement.executeUpdate("DROP TABLE IF EXISTS variables");
        statement.executeUpdate("CREATE TABLE IF NOT EXISTS variables (var BIG INT UNSIGNED NOT NULL PRIMARY KEY, val BIG INT UNSIGNED NOT NULL)");
    }

    @Override
    @SneakyThrows
    protected void createVariables(long nKey) {
        var insertStmt = connection.prepareStatement("INSERT INTO variables (var, val) values (?, 0)");
        for (long k = 1; k <= nKey; k++) {
            insertStmt.setLong(1, k);
            insertStmt.addBatch();
        }
        insertStmt.executeBatch();
    }

    @Override
    @SneakyThrows
    protected void dropDatabase() {
        // do noting
    }

    @Override
    public void start() {
        // do noting
    }

    @Override
    public void shutdown() {
        // do nothing
    }
}
