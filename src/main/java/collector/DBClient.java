package collector;

import history.Operation;
import history.Session;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;

@Slf4j
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

    @SneakyThrows
    public void execSession(Session<Long, Long> session, IsolationLevel isolationLevel) {
        var readStmt = connection.prepareStatement("SELECT * FROM dbtest.variables WHERE var=?");
        var writeStmt = connection.prepareStatement("UPDATE dbtest.variables SET val=? WHERE var=?");

        connection.setAutoCommit(false);
        connection.setTransactionIsolation(isolationLevel.getConstant());

        for (var transaction : session.getTransactions()) {
            // restart the transaction several times if failed
            for (int i = 0; !transaction.isSuccess() && i < maxRestartTimes; ++i) {
                try {
                    for (var op : transaction.getOps()) {
                        if (op.getType() == Operation.Type.WRITE) {
                            writeStmt.setLong(1, op.getValue());
                            writeStmt.setLong(2, op.getKey());
                            writeStmt.executeUpdate();
                        } else {
                            readStmt.setLong(1, op.getKey());
                            ResultSet resultSet = readStmt.executeQuery();
                            if (resultSet.next()) {
                                op.setValue(resultSet.getLong("val"));
                            }
                        }
                    }
                    connection.commit();
                    transaction.setSuccess(true);
                    log.trace("transaction {} success", transaction.getId());
                    break;
                } catch (SQLException e) {
                    connection.rollback();
                }
            }
            if (!transaction.isSuccess()) {
                log.warn("transaction {} failed after restarting {} times", transaction.getId(), maxRestartTimes);
            }
        }
    }

    @SneakyThrows
    public void close() {
        if (connection != null) {
            connection.close();
        }
    }
}
