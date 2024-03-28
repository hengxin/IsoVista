package collector.postgresql_reproduce;

import collector.DBClient;
import collector.IsolationLevel;
import history.Operation;
import history.Session;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Random;

@Slf4j
public class PostgreSQLClient extends DBClient {
    public PostgreSQLClient(String url, String username, String password) {
        super(url, username, password);
    }

    @SneakyThrows
    @Override
    public void execSession(Session<Long, Long> session, IsolationLevel isolationLevel) {
        var readStmt = connection.prepareStatement("SELECT (val) FROM dbtest.variables WHERE var=?");
        var insertStmt = connection.prepareStatement("INSERT INTO dbtest.variables (var, val) VALUES (?, ?) ON CONFLICT (var) DO UPDATE SET val = (?)");
//        var writeStmt = connection.prepareStatement("UPDATE dbtest.variables SET val=? WHERE var=?");

        connection.setAutoCommit(false);
        connection.setTransactionIsolation(isolationLevel.getConstant());

        for (var transaction : session.getTransactions()) {
            // restart the transaction several times if failed
            for (int i = 0; !transaction.isSuccess() && i < maxRestartTimes; ++i) {
                try {
                    for (var op : transaction.getOps()) {
                        if (op.getType() == Operation.Type.WRITE) {
                            insertStmt.setInt(1, op.getKey().intValue());
                            insertStmt.setInt(2, op.getValue().intValue());
                            insertStmt.setInt(3, op.getValue().intValue());
                            insertStmt.executeUpdate();
//                            if (!map.containsKey(op.getKey()) || !map.get(op.getKey())) {
//                                insertStmt.setLong(1, op.getKey());
//                                insertStmt.setLong(2, op.getValue());
//                                insertStmt.executeUpdate();
//                                map.put(op.getKey(), true);
//                            } else {
//                                writeStmt.setLong(1, op.getValue());
//                                writeStmt.setLong(2, op.getKey());
//                                writeStmt.executeUpdate();
//                            }
                        } else {
                            readStmt.setLong(1, op.getKey());
                            ResultSet resultSet = readStmt.executeQuery();
                            if (resultSet.next()) {
                                op.setValue(resultSet.getLong("val"));
                            } else {
                                op.setValue(0L);
                            }
                        }
                        Thread.sleep(new Random().nextInt(20));
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
}
