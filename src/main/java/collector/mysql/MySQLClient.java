package collector.mysql;

import collector.DBClient;
import history.History;
import history.Operation;
import history.Session;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Savepoint;

@Slf4j
public class MySQLClient extends DBClient {

    public MySQLClient(String url, String username, String password) {
        super(url, username, password);
    }

    @Override
    @SneakyThrows
    public void execSession(Session<Long, Long> session) {
        var readStmt = connection.prepareStatement("SELECT * FROM dbtest.variables WHERE var=?");
        var writeStmt = connection.prepareStatement("UPDATE dbtest.variables SET val=? WHERE var=?");

        connection.setAutoCommit(false);
        connection.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);

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
                    transaction.setSuccess(true);
                } catch (SQLException e) {
                    connection.rollback();
                }
            }
            if (transaction.isSuccess()) {
                connection.commit();
            } else {
                log.warn("transaction {} failed after restarting {} times", transaction.getId(), maxRestartTimes);
                connection.rollback();
            }
        }
    }
}
