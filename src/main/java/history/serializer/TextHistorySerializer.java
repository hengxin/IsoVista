package history.serializer;

import history.History;
import history.Operation;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;

public class TextHistorySerializer implements HistorySerializer<Long, Long> {
    @Override
    public void serializeHistory(History<Long, Long> history, String path) {
        try (var out = new BufferedWriter(new FileWriter(path))) {
            // Step 1: Loop through all transactions in the history
            for (var txn : history.getFlatTransactions()) {
                // Step 2: Loop through all operations in each transaction
                for (var op : txn.getOps()) {
                    // Step 3: Determine the operation type (READ or WRITE)
                    String opType = op.getType() == Operation.Type.READ ? "r" : "w";
                    // Step 4: Get the key and value of the operation
                    long key = op.getKey();
                    long value = op.getValue();
                    // Step 5: Get the session and transaction ID of the operation
                    long session = txn.getSession().getId();
                    long txnId = txn.getId();
                    // Step 7: Write the operation in the specified format to the file
                    // txnId == -1 => aborted
                    if (txnId == -1) {
                        out.write(String.format("w(%d,%d,%d,-1)%n", key, value, session));
                    } else {
                        out.write(String.format("%s(%d,%d,%d,%d)%n", opType, key, value, session, txnId));
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }
}
