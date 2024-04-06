package checker.PolySI.graph;


import com.google.common.graph.MutableValueGraph;
import com.google.common.graph.ValueGraphBuilder;
import history.History;
import history.Transaction;
import history.loader.ElleHistoryLoader;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.tuple.Pair;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;

import static history.Operation.Type.READ;
import static history.Operation.Type.WRITE;

@SuppressWarnings("UnstableApiUsage")
@Getter
public class KnownGraph<KeyType, ValueType> {
    @Setter
    private static boolean isElleHistory = false;

    private final MutableValueGraph<Transaction<KeyType, ValueType>, Collection<Edge<KeyType>>> readFrom = ValueGraphBuilder
            .directed().build();
    private final MutableValueGraph<Transaction<KeyType, ValueType>, Collection<Edge<KeyType>>> knownGraphA = ValueGraphBuilder
            .directed().build();
    private final MutableValueGraph<Transaction<KeyType, ValueType>, Collection<Edge<KeyType>>> knownGraphB = ValueGraphBuilder
            .directed().build();

    /**
     * Build a graph from a history
     *
     * The built graph contains SO and WR edges
     */
    public KnownGraph(History<KeyType, ValueType> history) {
        history.getTransactions().values().forEach(txn -> {
            knownGraphA.addNode(txn);
            knownGraphB.addNode(txn);
            readFrom.addNode(txn);
        });

        // add SO edges
        history.getSessions().values().forEach(session -> {
            Transaction<KeyType, ValueType> prevTxn = null;
            for (var txn : session.getTransactions()) {
                if (prevTxn != null) {
                    addEdge(knownGraphA, prevTxn, txn,
                            new Edge<>(EdgeType.SO, null));
                }
                prevTxn = txn;
            }
        });

        // add WR edges
        var writes = new HashMap<Pair<KeyType, ValueType>, Transaction<KeyType, ValueType>>();
        var events = history.getOperations();

        events.stream().filter(e -> e.getType() == WRITE).forEach(ev -> writes
                .put(Pair.of(ev.getKey(), ev.getValue()), ev.getTransaction()));

        events.stream().filter(e -> e.getType() == READ).forEach(ev -> {
            var writeTxn = writes.get(Pair.of(ev.getKey(), ev.getValue()));
            var txn = ev.getTransaction();

            if (writeTxn == txn) {
                return;
            }

            putEdge(writeTxn, txn, new Edge<KeyType>(EdgeType.WR, ev.getKey()));
        });

        // add WW edges
        if (isElleHistory) {
            events.stream().filter(e -> e.getType() == READ).forEach(ev -> {
                var elleValue = (ElleHistoryLoader.ElleValue) ev.getValue();
                Integer preVal = null;
                for(int i = 0; i < elleValue.getList().size(); i++) {
                    var prevWrite = writes.get(Pair.of(ev.getKey(), new ElleHistoryLoader.ElleValue(preVal, null)));
                    var nextWrite = writes.get(Pair.of(ev.getKey(), new ElleHistoryLoader.ElleValue(elleValue.getList().get(i), null)));
                    if (prevWrite != nextWrite) {
                        putEdge(prevWrite, nextWrite, new Edge<KeyType>(EdgeType.WW, ev.getKey()));
                    }
                    preVal = elleValue.getList().get(i);
                }
            });
        }
    }

    public void putEdge(Transaction<KeyType, ValueType> u,
            Transaction<KeyType, ValueType> v, Edge<KeyType> edge) {
//        System.out.println("put edge " + u.getId() + " " + v.getId() + " " + edge);
        switch (edge.getType()) {
        case WR:
            addEdge(readFrom, u, v, edge);
            // fallthrough
        case WW:
        case SO:
            addEdge(knownGraphA, u, v, edge);
            break;
        case RW:
            addEdge(knownGraphB, u, v, edge);
            break;
        }
    }

    public void removeEdge(Transaction<KeyType, ValueType> u, Transaction<KeyType, ValueType> v, Edge<KeyType> edge) {
        switch (edge.getType()) {
            case WR:
                removeEdge(readFrom, u, v, edge);
                // fallthrough
            case WW:
            case SO:
                removeEdge(knownGraphA, u, v, edge);
                break;
            case RW:
                removeEdge(knownGraphB, u, v, edge);
                break;
        }
    }

    private void addEdge(
            MutableValueGraph<Transaction<KeyType, ValueType>, Collection<Edge<KeyType>>> graph,
            Transaction<KeyType, ValueType> u,
            Transaction<KeyType, ValueType> v, Edge<KeyType> edge) {
        if (!graph.hasEdgeConnecting(u, v)) {
            graph.putEdgeValue(u, v, new ArrayList<>());
        }
        if (graph.edgeValue(u, v).get().contains(edge)) {
            return;
        }
        graph.edgeValue(u, v).get().add(edge);
    }

    private void removeEdge(
            MutableValueGraph<Transaction<KeyType, ValueType>, Collection<Edge<KeyType>>> graph,
            Transaction<KeyType, ValueType> u,
            Transaction<KeyType, ValueType> v, Edge<KeyType> edge) {
        if (!graph.hasEdgeConnecting(u, v)) {
            return;
        }
        graph.edgeValue(u, v).get().removeIf(e -> e.equals(edge));
        if (graph.edgeValue(u, v).get().isEmpty()) {
            graph.removeEdge(u, v);
        }
    }
}
