package collector.mysql;

import collector.DBClient;
import history.Operation;
import history.Session;
import lombok.SneakyThrows;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Savepoint;

public class MySQLClient extends DBClient {

    public MySQLClient(String url, String username, String password) {
        super(url, username, password);
    }

    @Override
    @SneakyThrows
    public void execSession(Session<Long, Long> session) {
        var readStmt = connection.prepareStatement("SELECT * FROM dbtest.variables WHERE key=?");
        var writeStmt = connection.prepareStatement("UPDATE dbtest.variables SET val=? WHERE key=?");

        connection.setAutoCommit(false);
        connection.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);

        for (var transaction : session.getTransactions()) {
            Savepoint savepoint = connection.setSavepoint();

            for (var op : transaction.getOps()) {
                if (op.getType() == Operation.Type.WRITE) {
                    writeStmt.setLong(1, op.getValue());
                    writeStmt.setLong(2, op.getKey());
                    try {
                        writeStmt.executeUpdate();
                    } catch (SQLException e) {
                        connection.rollback(savepoint);
                        break;
                    }
                } else {
                    readStmt.setLong(1, op.getKey());
                    try (ResultSet resultSet = readStmt.executeQuery()) {
                        if (resultSet.next()) {
                            op.setValue(resultSet.getLong("val"));
                        } else {
                            connection.rollback(savepoint);
                            break;
                        }
                    } catch (SQLException e) {
                        connection.rollback(savepoint);
                        break;
                    }
                }
            }
            connection.commit();
        }
    }
}
