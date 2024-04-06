package history.serializer;

import history.History;
import history.Operation;
import history.Transaction;
import history.loader.ElleHistoryLoader;
import lombok.SneakyThrows;
import org.apache.commons.lang3.tuple.Pair;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;

import static history.Operation.Type.READ;
import static history.Operation.Type.WRITE;

public class ElleTextHistorySerializer implements HistorySerializer<Integer, ElleHistoryLoader.ElleValue> {

    @Override
    @SneakyThrows
    public void serializeHistory(History<Integer, ElleHistoryLoader.ElleValue> history, String path) {
        // serialize an elle history to text
        var textOut = new BufferedWriter(new FileWriter(path));
        var txns = history.getFlatTransactions();
        txns.sort(Comparator.comparingLong(Transaction::getId));
        for (var txn : txns) {
            for (var op : txn.getOps()) {
                String opType = op.getType() == Operation.Type.READ ? "r" : "w";
                int key = op.getKey();
                long value = op.getValue().getLastElement() == null ? 0 : op.getValue().getLastElement();
                long session = txn.getSession().getId();
                long txnId = txn.getId();

                textOut.write(String.format("%s(%d,%d,%d,%d)\n", opType, key, value, session, txnId));
            }
        }
        textOut.close();

        // serialize known ww edges
        var writes = new HashMap<Pair<Integer, ElleHistoryLoader.ElleValue>, Transaction<Integer, ElleHistoryLoader.ElleValue>>();
        var events = history.getOperations();

        events.stream().filter(e -> e.getType() == WRITE).forEach(ev -> writes.put(Pair.of(ev.getKey(), ev.getValue()), ev.getTransaction()));

        // serialize WW edges
        var knownWW = new HashSet<Pair<Long, Long>>();
        var wwOut = new BufferedWriter(new FileWriter(path + "_ww"));
        events.stream().filter(e -> e.getType() == READ).forEach(ev -> {
            var elleValue = (ElleHistoryLoader.ElleValue) ev.getValue();
            Integer preVal = null;
            for(int i = 0; i < elleValue.getList().size(); i++) {
                var prevWrite = writes.get(Pair.of(ev.getKey(), new ElleHistoryLoader.ElleValue(preVal, null)));
                var nextWrite = writes.get(Pair.of(ev.getKey(), new ElleHistoryLoader.ElleValue(elleValue.getList().get(i), null)));
                if (prevWrite != null && nextWrite != null && prevWrite != nextWrite) {
                    knownWW.add(Pair.of(prevWrite.getId(), nextWrite.getId()));
                }
                preVal = elleValue.getList().get(i);
            }
        });
        knownWW.forEach(ww -> {
            try {
                wwOut.write(String.format("%d -> %d\n", ww.getLeft(), ww.getRight()));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        wwOut.close();
    }
}
