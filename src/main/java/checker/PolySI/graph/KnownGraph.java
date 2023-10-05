package checker.PolySI.graph;


import com.google.common.graph.MutableValueGraph;
import com.google.common.graph.ValueGraphBuilder;
import history.History;
import history.Transaction;
import lombok.Getter;
import org.apache.commons.lang3.tuple.Pair;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;

import static history.Operation.Type.READ;
import static history.Operation.Type.WRITE;

@SuppressWarnings("UnstableApiUsage")
@Getter
public class KnownGraph<KeyType, ValueType> {
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
    }

    public void putEdge(Transaction<KeyType, ValueType> u,
            Transaction<KeyType, ValueType> v, Edge<KeyType> edge) {
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

    private void addEdge(
            MutableValueGraph<Transaction<KeyType, ValueType>, Collection<Edge<KeyType>>> graph,
            Transaction<KeyType, ValueType> u,
            Transaction<KeyType, ValueType> v, Edge<KeyType> edge) {
        if (!graph.hasEdgeConnecting(u, v)) {
            graph.putEdgeValue(u, v, new ArrayList<>());
        }
        graph.edgeValue(u, v).get().add(edge);
    }
}
