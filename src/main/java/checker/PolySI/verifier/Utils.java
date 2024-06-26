package checker.PolySI.verifier;

import checker.PolySI.graph.Edge;
import checker.PolySI.graph.EdgeType;
import checker.PolySI.graph.KnownGraph;
import checker.PolySI.graph.MatrixGraph;
import com.google.common.collect.Sets;
import com.google.common.collect.Streams;
import com.google.common.graph.EndpointPair;
import com.google.common.graph.MutableValueGraph;
import com.google.common.graph.ValueGraphBuilder;
import history.History;
import history.Operation;
import history.Transaction;
import monosat.Lit;
import monosat.Logic;
import monosat.Solver;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class Utils {
    public static <KeyType, ValueType> Transaction<KeyType, ValueType> verifyInternalConsistency(History<KeyType, ValueType> history) {
        var writes = new HashMap<Pair<KeyType, ValueType>, Pair<Operation<KeyType, ValueType>, Integer>>();
        var txnWrites = new HashMap<Pair<Transaction<KeyType, ValueType>, KeyType>, ArrayList<Integer>>();
        var getEvents = ((Function<Operation.Type, Stream<Pair<Integer, Operation<KeyType, ValueType>>>>) type -> history
                .getTransactions().values().stream().flatMap(txn -> {
                    var events = txn.getOps();
                    return IntStream.range(0, events.size()).mapToObj(i -> Pair.of(i, events.get(i)))
                            .filter(p -> p.getRight().getType() == type);
                }));

        getEvents.apply(Operation.Type.WRITE).forEach(p -> {
            var i = p.getLeft();
            var ev = p.getRight();
            writes.put(Pair.of(ev.getKey(), ev.getValue()), Pair.of(ev, i));
            txnWrites.computeIfAbsent(Pair.of(ev.getTransaction(), ev.getKey()), k -> new ArrayList()).add(i);
        });

        for (var p : getEvents.apply(Operation.Type.READ).collect(Collectors.toList())) {
            var i = p.getLeft();
            var ev = p.getRight();
            var writeEv = writes.get(Pair.of(ev.getKey(), ev.getValue()));

            if (writeEv == null) {
                System.err.printf("%s has no corresponding write\n", ev);
                return ev.getTransaction();
            }

            var myWriteIndices = txnWrites.getOrDefault(Pair.of(ev.getTransaction(), ev.getKey()), new ArrayList<>());
            var writeIndices = txnWrites.get(Pair.of(writeEv.getLeft().getTransaction(), writeEv.getLeft().getKey()));
            var j = Collections.binarySearch(writeIndices, writeEv.getRight());

            if (writeEv.getLeft().getTransaction() == ev.getTransaction()) {
                if (j != writeIndices.size() - 1 && writeIndices.get(j + 1) < i) {
                    System.err.printf("%s not reading from latest write: %s\n", ev, writeEv.getLeft());
                    return ev.getTransaction();
                } else if (writeEv.getRight() > i) {
                    System.err.printf("%s reads from a write after it: %s\n", ev, writeEv.getLeft());
                    return ev.getTransaction();
                }
            } else if (j != writeIndices.size() - 1 || (!myWriteIndices.isEmpty() && myWriteIndices.get(0) < i)) {
                System.err.printf("%s not reading from latest write: %s\n", ev, writeEv.getLeft());
                return ev.getTransaction();
            }
        }
        return null;
    }

    /**
     * Collect unknown edges
     *
     * @param graphA       graph A containing known and unknown edges
     * @param graphB       graph B containing known and unknown edges
     * @param reachability known reachable node pairs. Edges that connect reachable
     *                     pairs are not collected
     * @param solver       SAT solver
     */
    static <KeyType, ValueType> List<Triple<Transaction<KeyType, ValueType>, Transaction<KeyType, ValueType>, Lit>> getUnknownEdges(
            MutableValueGraph<Transaction<KeyType, ValueType>, Collection<Lit>> graphA,
            MutableValueGraph<Transaction<KeyType, ValueType>, Collection<Lit>> graphB,
            MatrixGraph<Transaction<KeyType, ValueType>> reachability, Solver solver) {
        var edges = new ArrayList<Triple<Transaction<KeyType, ValueType>, Transaction<KeyType, ValueType>, Lit>>();

        // predEdges: p ww-> n
        // p==s || p can reach s by known
        // succEdges: n rw-> s
        //
        for (var p : graphA.nodes()) {
            for (var n : graphA.successors(p)) {
                var predEdges = graphA.edgeValue(p, n).get();

                if (p == n || !reachability.hasEdgeConnecting(p, n)) {
                    predEdges.forEach(e -> edges.add(Triple.of(p, n, e)));
                }

                var txns = graphB.successors(n).stream()
                        .filter(t -> p == t || !reachability.hasEdgeConnecting(p, t))
                        .collect(Collectors.toList());

                for (var s : txns) {
                    var succEdges = graphB.edgeValue(n, s).get();
                    predEdges.forEach(e1 -> succEdges.forEach(e2 -> {
                        var lit = Logic.and(e1, e2);
                        solver.setDecisionLiteral(lit, false);
                        edges.add(Triple.of(p, s, lit));
                    }));
                }
            }
        }

        return edges;
    }

    /**
     * Collect unknown edges
     *
     * @param graph        graph containing known and unknown edges
     * @param reachability known reachable node pairs. Edges that connect reachable
     *                     pairs are not collected
     */
    static <KeyType, ValueType> List<Triple<Transaction<KeyType, ValueType>, Transaction<KeyType, ValueType>, Lit>> getUnknownEdges(
            MutableValueGraph<Transaction<KeyType, ValueType>, Collection<Lit>> graph,
            MatrixGraph<Transaction<KeyType, ValueType>> reachability) {
        var edges = new ArrayList<Triple<Transaction<KeyType, ValueType>, Transaction<KeyType, ValueType>, Lit>>();

        for (var p : graph.nodes()) {
            for (var n : graph.successors(p)) {
                var predEdges = graph.edgeValue(p, n).get();

                if (p == n || !reachability.hasEdgeConnecting(p, n)) {
                    predEdges.forEach(e -> edges.add(Triple.of(p, n, e)));
                }
            }
        }

        return edges;
    }

    /**
     * Collect known edges in A union C
     *
     * @param graphA known graph A
     * @param graphB known graph B
     * @param AC     the graph containing the edges to collect
     */
    static <KeyType, ValueType> List<Triple<Transaction<KeyType, ValueType>, Transaction<KeyType, ValueType>, Lit>> getKnownEdges(
            MutableValueGraph<Transaction<KeyType, ValueType>, Collection<Lit>> graphA,
            MutableValueGraph<Transaction<KeyType, ValueType>, Collection<Lit>> graphB,
            MatrixGraph<Transaction<KeyType, ValueType>> AC) {
        return AC.edges().stream().map(e -> {
            var n = e.source();
            var m = e.target();
            var firstEdge = ((Function<Optional<Collection<Lit>>, Lit>) c -> c.get().iterator().next());

            if (graphA.hasEdgeConnecting(n, m)) {
                return Triple.of(n, m, firstEdge.apply(graphA.edgeValue(n, m)));
            }

            var middle = Sets.intersection(graphA.successors(n), graphB.predecessors(m)).iterator().next();
            return Triple.of(n, m, Logic.and(firstEdge.apply(graphA.edgeValue(n, middle)),
                    firstEdge.apply(graphB.edgeValue(middle, m))));
        }).collect(Collectors.toList());
    }

    /**
     * Collect known edges
     *
     * @param graph known graph
     * @param AC     the graph containing the edges to collect
     */
    static <KeyType, ValueType> List<Triple<Transaction<KeyType, ValueType>, Transaction<KeyType, ValueType>, Lit>> getKnownEdges(
            MutableValueGraph<Transaction<KeyType, ValueType>, Collection<Lit>> graph,
            MatrixGraph<Transaction<KeyType, ValueType>> AC) {
        return AC.edges().stream().map(e -> {
            var n = e.source();
            var m = e.target();
            var firstEdge = ((Function<Optional<Collection<Lit>>, Lit>) c -> c.get().iterator().next());

            if (graph.hasEdgeConnecting(n, m)) {
                return Triple.of(n, m, firstEdge.apply(graph.edgeValue(n, m)));
            } else {
                throw new RuntimeException("Can not reach here");
            }
        }).collect(Collectors.toList());
    }

    static <KeyType, ValueType> Map<Transaction<KeyType, ValueType>, Integer> getOrderInSession(
            History<KeyType, ValueType> history) {
        // @formatter:off
        return history.getSessions().values().stream()
                .flatMap(s -> Streams.zip(
                    s.getTransactions().stream(),
                    IntStream.range(0, s.getTransactions().size()).boxed(),
                    Pair::of))
                .collect(Collectors.toMap(Pair::getKey, Pair::getValue));
        // @formatter:on
    }

    static <KeyType, ValueType> MutableValueGraph<Transaction<KeyType, ValueType>, Collection<Lit>> createEmptyGraph(
            History<KeyType, ValueType> history) {
        MutableValueGraph<Transaction<KeyType, ValueType>, Collection<Lit>> g = ValueGraphBuilder.directed()
                .allowsSelfLoops(true).build();

        history.getTransactions().values().forEach(g::addNode);
        return g;
    }

    static <KeyType, ValueType> MutableValueGraph<Transaction<KeyType, ValueType>, Collection<Lit>>
    mergeGraph(MutableValueGraph<Transaction<KeyType, ValueType>, Collection<Lit>> g1,
               MutableValueGraph<Transaction<KeyType, ValueType>, Collection<Lit>> g2) {
        MutableValueGraph<Transaction<KeyType, ValueType>, Collection<Lit>> g = ValueGraphBuilder.directed()
                .allowsSelfLoops(true).build();
        g1.nodes().forEach(g::addNode);
        g2.nodes().forEach(g::addNode);

        g1.edges().forEach(e -> {
            g1.edgeValue(e).get().forEach(lit -> {
                addEdge(g, e.source(), e.target(), lit);
            });
        });
        g2.edges().forEach(e -> {
            g2.edgeValue(e).get().forEach(lit -> {
                addEdge(g, e.source(), e.target(), lit);
            });
        });
        return g;
    }

    static <KeyType, ValueType> void addEdge(MutableValueGraph<Transaction<KeyType, ValueType>, Collection<Lit>> g,
            Transaction<KeyType, ValueType> src, Transaction<KeyType, ValueType> dst, Lit lit) {
        if (!g.hasEdgeConnecting(src, dst)) {
            g.putEdgeValue(src, dst, new ArrayList<>());
        }
        g.edgeValue(src, dst).get().add(lit);
    }

    /*
     * Delete edges in a way that preserves reachability
     */
    static <KeyType, ValueType> MatrixGraph<Transaction<KeyType, ValueType>> reduceEdges(
            MatrixGraph<Transaction<KeyType, ValueType>> graph,
            Map<Transaction<KeyType, ValueType>, Integer> orderInSession) {
        System.err.printf("Before: %d edges\n", graph.edges().size());
        var newGraph = MatrixGraph.ofNodes(graph);

        for (var n : graph.nodes()) {
            var succ = graph.successors(n);
            // @formatter:off
            var firstInSession = succ.stream()
                .collect(Collectors.toMap(
                    m -> m.getSession(),
                    Function.identity(),
                    (p, q) -> orderInSession.get(p)
                        < orderInSession.get(q) ? p : q));

            firstInSession.values().forEach(m -> newGraph.putEdge(n, m));

            succ.stream()
                .filter(m -> m.getSession() == n.getSession() && m != n
                        && orderInSession.get(m) == orderInSession.get(n) + 1)
                .forEach(m -> newGraph.putEdge(n, m));
            // @formatter:on
        }

        System.err.printf("After: %d edges\n", newGraph.edges().size());
        return newGraph;
    }
    public static <KeyType, ValueType> String intConflictToDot(Transaction<KeyType, ValueType> txn) {
        var builder = new StringBuilder();
        builder.append("digraph {\n");
        builder.append(String.format("\"%s\" [ops=\"%s\"];\n", txn, txn.getOps()));
        builder.append("}\n");
        return builder.toString();
    }

    public static <KeyType, ValueType> String conflictsToDot(String anomaly, Collection<Transaction<KeyType, ValueType>> transactions,
                                                      Map<EndpointPair<Transaction<KeyType, ValueType>>, Collection<Edge<KeyType>>> edges,
                                                      Map<Transaction<KeyType, ValueType>, Collection<EndpointPair<Transaction<KeyType, ValueType>>>> txnRelateToMap,
                                                      Map<EndpointPair<Transaction<KeyType, ValueType>>, Collection<EndpointPair<Transaction<KeyType, ValueType>>>> edgeRelateToMap,
                                                      HashMap<EndpointPair<Transaction<KeyType, ValueType>>, Collection<Edge<KeyType>>> oppositeEdges,
                                                      List<Pair<EndpointPair<Transaction<KeyType, ValueType>>, Collection<Edge<KeyType>>>> mainCycle) {
        Function<Collection<EndpointPair<Transaction<KeyType, ValueType>>>, String> edgesToStr = (edgeList) -> {
            if (edgeList == null || edgeList.isEmpty()) {
                return "";
            }
            var builder = new StringBuilder();
            for (var edge : edgeList) {
                builder.append(String.format("%s -> %s, ", edge.source(), edge.target()));
            }
            builder.delete(builder.length() - 2, builder.length());
            return builder.toString();
        };

        var builder = new StringBuilder();
        builder.append("digraph ").append(anomaly).append(" {\n");

        for (var txn : transactions) {
            boolean inCycle = mainCycle.stream().anyMatch(p -> p.getKey().source().equals(txn) || p.getKey().target().equals(txn));
            builder.append(String.format("\"%s\" [id=\"%s\" ops=\"%s\" relate_to=\"%s\" in_cycle=%s];\n",
                    txn, txn, txn.getOps(), edgesToStr.apply(txnRelateToMap.get(txn)), inCycle));
        }

        for (var e : edges.entrySet()) {
            var pair = e.getKey();
            var keys = e.getValue();
            var label = new StringBuilder();
            boolean inCycle = mainCycle.stream().anyMatch(p -> p.getKey().equals(pair));

            for (var k : keys) {
                if (k.getType() != EdgeType.SO) {
                    label.append(String.format("%s %s\\n", k.getType(), k.getKey()));
                } else {
                    label.append(String.format("%s\\n", k.getType()));
                }
            }

            builder.append(String.format("\"%s\" -> \"%s\" [id=\"%s -> %s\" label=\"%s\" relate_to=\"%s\" in_cycle=%s];\n",
                    pair.source(), pair.target(), pair.source(), pair.target(), label, edgesToStr.apply(edgeRelateToMap.get(pair)), inCycle));
        }

        for (var e : oppositeEdges.entrySet()) {
            var pair = e.getKey();
            var keys = e.getValue();
            var label = new StringBuilder();

            for (var k : keys) {
                if (k.getType() != EdgeType.SO) {
                    label.append(String.format("%s %s\\n", k.getType(), k.getKey()));
                } else {
                    label.append(String.format("%s\\n", k.getType()));
                }
            }

            builder.append(String.format("\"%s\" -> \"%s\" [id=\"%s -> %s\" label=\"%s\" relate_to=\"%s\" style=dotted];\n",
                    pair.source(), pair.target(), pair.source(), pair.target(), label, edgesToStr.apply(edgeRelateToMap.get(pair))));
        }

        builder.append("}\n");
        return builder.toString();
    }

    static <KeyType, ValueType> String conflictsToLegacy(Collection<Transaction<KeyType, ValueType>> transactions,
                                                         Collection<Pair<EndpointPair<Transaction<KeyType, ValueType>>, Collection<Edge<KeyType>>>> edges,
                                                         Collection<SIConstraint<KeyType, ValueType>> constraints, KnownGraph <KeyType, ValueType> knownGraph) {
        var builder = new StringBuilder();

        edges.forEach(p -> builder.append(String.format("Edge: %s\n", p)));
        constraints.forEach(c -> builder.append(String.format("Constraint: %s\n", c)));
        builder.append("Known edges:\n");
        knownGraph.getKnownGraphA().edges().stream()
                .map(e -> Pair.of(e, knownGraph.getKnownGraphA().edgeValue(e).get()))
                .forEach(p -> builder.append(String.format("%s,\n", p)));


//        builder.append(String.format("Related transactions:\n"));
//        transactions.forEach(t -> {
//            builder.append(String.format("sessionid: %d, id: %d\nops:\n", t.getSession().getId(), t.getId()));
//            t.getOps().forEach(e -> builder.append(String.format("%s %s = %s\n", e.getType(), e.getKey(), e.getValue())));
//        });

        return builder.toString();
    }
}
