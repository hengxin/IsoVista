package history.serializer;

import history.History;
import history.Operation;
import history.Transaction;
import history.loader.ElleHistoryLoader;
import lombok.SneakyThrows;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.util.Comparator;

public class ElleTextHistorySerializer implements HistorySerializer<Long, ElleHistoryLoader.ElleValue> {

    @Override
    @SneakyThrows
    public void serializeHistory(History<Long, ElleHistoryLoader.ElleValue> history, String path) {
        // serialize an elle history to text
        var out = new BufferedWriter(new FileWriter(path));
        var txns = history.getFlatTransactions();
        txns.sort(Comparator.comparingLong(Transaction::getId));
        for (var txn : txns) {
            for (var op : txn.getOps()) {
                String opType = op.getType() == Operation.Type.READ ? "r" : "w";
                long key = op.getKey();
                long value = op.getValue().getLastElement();
                long session = txn.getSession().getId();
                long txnId = txn.getId();

                out.write(String.format("%s(%d,%d,%d,%d)%n", opType, key, value, session, txnId));
            }
        }
    }
}
