package history.serializer;

import history.History;
import history.Operation;
import lombok.SneakyThrows;

import java.io.BufferedWriter;
import java.io.FileWriter;

/**
 * A serializer that writes a history to a text file.
 */
public class TextHistorySerializer implements HistorySerializer<Long, Long> {
    @Override
    @SneakyThrows
    public void serializeHistory(History<Long, Long> history, String path) {
        try(var out = new BufferedWriter(new FileWriter(path))) {
            for (var txn : history.getFlatTransactions()) {
                for (var op : txn.getOps()) {
                    String opType = op.getType() == Operation.Type.READ ? "r" : "w";
                    long key = op.getKey();
                    long value = op.getValue();
                    long session = txn.getSession().getId();
                    long txnId = txn.getId();

                    out.write(String.format("%s(%d,%d,%d,%d)%n", opType, key, value, session, txnId));
                }
            }
        }
    }
}
