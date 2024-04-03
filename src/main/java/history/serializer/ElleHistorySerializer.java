package history.serializer;

import history.History;
import history.Operation;
import history.Transaction;
import history.loader.ElleHistoryLoader;
import lombok.SneakyThrows;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Map;
import java.util.stream.Collectors;

public class ElleHistorySerializer implements HistorySerializer<Integer, ElleHistoryLoader.ElleValue> {
    private String opToEdn(Operation<Integer, ElleHistoryLoader.ElleValue> op) {
        return String.format("[ %s %d %s ]", op.getType() == Operation.Type.READ ? ":r" : ":append", op.getKey(), op.getValue().toEdn());
    }

    private String invokeOpToEdn(Operation<Integer, ElleHistoryLoader.ElleValue> op) {
        return String.format("[ %s %d %s ]",
                op.getType() == Operation.Type.READ ? ":r" : ":append",
                op.getKey(),
                op.getType() == Operation.Type.READ ? "nil" : op.getValue().toEdn());
    }

    private String txnToEdn(Transaction<Integer, ElleHistoryLoader.ElleValue> txn, long index) {
        var invokeOps = txn.getOps().stream()
                .map(this::invokeOpToEdn)
                .collect(Collectors.joining(" "));
        var resultOps = txn.getOps().stream()
                .map(this::opToEdn)
                .collect(Collectors.joining(" "));

        String invoke = String.format("{:index %d, :type :invoke, :process %d, :value [ %s ]}\n", index, txn.getSession().getId(), invokeOps);
        String result = String.format("{:index %d, :type :ok, :process %d, :value [ %s ]}\n", index + 1, txn.getSession().getId(), resultOps); // assume all txn success
        return invoke + result;
    }


    @Override
    @SneakyThrows
    public void serializeHistory(History<Integer, ElleHistoryLoader.ElleValue> history, String path) {
        final int[] index = {1};
        var out = new BufferedWriter(new FileWriter(path));
        history.getTransactions().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    // assume the history has no init transaction
                    try {
                        out.write(txnToEdn(entry.getValue(), index[0]));
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                    index[0] += 2;
                });
        out.close();
    }
}
