package history;

import javafx.util.Pair;
import lombok.Data;
import org.checkerframework.checker.units.qual.K;

import java.util.*;
import java.util.stream.Collectors;

@Data
public class History<KeyType, ValType> {
    private final Map<Long, Session<KeyType, ValType>> sessions = new HashMap<>();
    private final Map<Long, Transaction<KeyType, ValType>> transactions = new HashMap<>();

    private final Set<Pair<KeyType, ValType>> abortedWrites = new HashSet<>();
    private final Set<KeyType> keySet = new HashSet<>();

    public Session<KeyType, ValType> getSession(long id) {
        return sessions.get(id);
    }

    public Transaction<KeyType, ValType> getTransaction(long id) {
        return transactions.get(id);
    }

    public Session<KeyType, ValType> addSession(long id) {
        var session = new Session<KeyType, ValType>(id);
        sessions.put(id, session);
        return session;
    }

    public Transaction<KeyType, ValType> addTransaction(Session<KeyType, ValType> session, long id) {
        var txn = new Transaction<>(id, session);
        transactions.put(id, txn);
        session.getTransactions().add(txn);
        return txn;
    }

    public Operation<KeyType, ValType> addOperation(Transaction<KeyType, ValType> transaction, Operation.Type type, KeyType variable, ValType value) {
        var operation = new Operation<>(type, variable, value, transaction, transaction.getOps().size());
        transaction.getOps().add(operation);
        keySet.add(variable);
        return operation;
    }

    public void addAbortedWrite(KeyType variable, ValType value) {
        abortedWrites.add(new Pair<>(variable, value));
    }

    public List<Operation<KeyType, ValType>> getOperations() {
        return transactions.values().stream().flatMap(txn -> txn.ops.stream()).collect(Collectors.toList());
    }

    public List<Transaction<KeyType, ValType>> getFlatTransactions() {
        int maxLength = sessions.values().stream()
                .map(Session::getTransactions)
                .map(List::size)
                .max(Integer::compareTo)
                .orElse(0);
        var result = new LinkedList<Transaction<KeyType, ValType>>();
        for (int i = 0; i < maxLength; ++i) {
            for (Session<KeyType, ValType> session : sessions.values()) {
                if (session.getTransactions().size() <= i) {
                    continue;
                }
                result.add(session.getTransactions().get(i));
            }
        }
        return result;
    }

    public void addInitSession() {
        Session<KeyType, ValType> initSession = addSession(-1);
        Transaction<KeyType, ValType> initTransaction = addTransaction(initSession, -1);
        for (var i : keySet) {
            addOperation(initTransaction, Operation.Type.WRITE, i, (ValType) (Object)0L);
        }
        initTransaction.setSuccess(true);
    }

    public void removeInitSession() {
        sessions.remove(-1L);
    }
}
