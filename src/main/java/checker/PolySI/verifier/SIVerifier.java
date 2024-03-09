package checker.PolySI.verifier;


import checker.PolySI.graph.Edge;
import checker.PolySI.graph.EdgeType;
import checker.PolySI.graph.KnownGraph;
import checker.PolySI.util.Recursive;
import checker.PolySI.util.TriConsumer;
import com.google.common.collect.Sets;
import com.google.common.graph.EndpointPair;
import history.Operation;
import history.History;
import history.Transaction;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.*;
import java.util.stream.Collectors;

import lombok.Getter;
import lombok.Setter;
import lombok.SneakyThrows;
import org.apache.commons.lang3.SerializationUtils;
import org.apache.commons.lang3.tuple.Pair;

import org.checkerframework.checker.units.qual.K;
import util.Profiler;
import util.TriFunction;

@SuppressWarnings("UnstableApiUsage")
public class SIVerifier<KeyType, ValueType> {
    private final History<KeyType, ValueType> history;

    @Getter
    @Setter
    private static boolean coalesceConstraints = true;

    private Pair<Collection<Pair<EndpointPair<Transaction<KeyType, ValueType>>, Collection<Edge<KeyType>>>>, Collection<SIConstraint<KeyType, ValueType>>> conflicts;

    private Transaction<KeyType, ValueType> intConflict;

    public SIVerifier(History<KeyType, ValueType> history) {
        this.history = history;
    }

    public boolean audit() {
        var profiler = Profiler.getInstance();

        profiler.startTick("ONESHOT_CONS");
        profiler.startTick("SI_VERIFY_INT");
        var satisfy_int = Utils.verifyInternalConsistency(history);
        profiler.endTick("SI_VERIFY_INT");
        if (satisfy_int != null) {
            intConflict = satisfy_int;
            return false;
        }

        profiler.startTick("SI_GEN_PREC_GRAPH");
        var graph = new KnownGraph<>(history);
        profiler.endTick("SI_GEN_PREC_GRAPH");
        System.err.printf("Known edges: %d\n", graph.getKnownGraphA().edges().size());

        profiler.startTick("SI_GEN_CONSTRAINTS");
        var constraints = generateConstraints(history, graph);
        profiler.endTick("SI_GEN_CONSTRAINTS");
        System.err.printf("Constraints count: %d\nTotal edges in constraints: %d\n", constraints.size(),
                constraints.stream().map(c -> c.getEdges1().size() + c.getEdges2().size()).reduce(0, Integer::sum));
        profiler.endTick("ONESHOT_CONS");

        var hasLoop = Pruning.pruneConstraints(graph, constraints, history);
        if (hasLoop) {
            System.err.printf("Cycle found in pruning\n");
        }
        System.err.printf("After Prune:\n" + "Constraints count: %d\nTotal edges in constraints: %d\n",
                constraints.size(),
                constraints.stream().map(c -> c.getEdges1().size() + c.getEdges2().size()).reduce(0, Integer::sum));

        profiler.startTick("ONESHOT_SOLVE");
        var solver = new SISolver<>(history, graph, constraints);

        boolean accepted = solver.solve();
        profiler.endTick("ONESHOT_SOLVE");

        if (!accepted) {
            conflicts = solver.getConflicts();
        }

        return accepted;
    }

    /**
     * Generates a dot file and outputs it to the specified path. The dot file represents the conflicts in the history.
     *
     * @param  path	The path where the dot file will be saved.
     */
    @SneakyThrows
    public void outputDotFile(String path) {
        if (intConflict != null) {
            var dotOutputStr = Utils.intConflictToDot(intConflict);
            System.out.print(dotOutputStr);
            Files.writeString(Path.of(path), dotOutputStr, StandardOpenOption.CREATE);
            return;
        }

        if (conflicts == null) {
            return;
        }

        var graphBeforePruning = new KnownGraph<>(history);
        var constraintsBeforePruning = generateConstraints(history, graphBeforePruning);

        // result graph
        var txns = new HashSet<Transaction<KeyType, ValueType>>();
        var edges = new HashMap<EndpointPair<Transaction<KeyType, ValueType>>, Collection<Edge<KeyType>>>();
        var txnRelateToMap = new HashMap<Transaction<KeyType, ValueType>, Collection<EndpointPair<Transaction<KeyType, ValueType>>>>();
        var edgeRelateToMap = new HashMap<EndpointPair<Transaction<KeyType, ValueType>>, Collection<EndpointPair<Transaction<KeyType, ValueType>>>>();

        var visited = new HashMap<EndpointPair<Transaction<KeyType, ValueType>>, Collection<Edge<KeyType>>>();
        // map an edge to its opposite edge. e.g. an edge in c.either, map it to c.or
        var oppositeEdgesMap = new HashMap<Pair<EndpointPair<Transaction<KeyType, ValueType>>, Edge<KeyType>>, Collection<Pair<EndpointPair<Transaction<KeyType, ValueType>>, Edge<KeyType>>>>();
        // map an edge to its constraint
        var edgeConstraintMap = new HashMap<Pair<EndpointPair<Transaction<KeyType, ValueType>>, Edge<KeyType>>, SIConstraint<KeyType, ValueType>>();

        Function<SIEdge<KeyType, ValueType>, Pair<EndpointPair<Transaction<KeyType, ValueType>>, Edge<KeyType>>> SIEdgeToPair = e -> Pair.of(EndpointPair.ordered(e.getFrom(), e.getTo()), new Edge<>(e.getType(), e.getKey()));
        Function<Pair<EndpointPair<Transaction<KeyType, ValueType>>, Edge<KeyType>>, SIEdge<KeyType, ValueType>> PairToSIEdge = e -> new SIEdge<>(e.getLeft().source(), e.getLeft().target(), e.getRight().getType(), e.getRight().getKey());

        // construct opposite edges map and edge to constraint map
        for (var constraint : constraintsBeforePruning) {
            for (var eitherEdge : constraint.getEdges1()) {
                edgeConstraintMap.put(SIEdgeToPair.apply(eitherEdge), constraint);
                oppositeEdgesMap.put(SIEdgeToPair.apply(eitherEdge), constraint.getEdges2().stream()
                        .map(SIEdgeToPair)
                        .collect(Collectors.toList()));
            }
            for (var orEdge : constraint.getEdges2()) {
                edgeConstraintMap.put(SIEdgeToPair.apply(orEdge), constraint);
                oppositeEdgesMap.put(SIEdgeToPair.apply(orEdge), constraint.getEdges1().stream()
                        .map(SIEdgeToPair)
                        .collect(Collectors.toList()));
            }
        }

        BiFunction<EndpointPair<Transaction<KeyType, ValueType>>, Edge<KeyType>, Void> addEdge = (pair, edge) -> {
            if (!edges.containsKey(pair)) {
                edges.put(pair, Sets.newHashSet(edge));
                return null;
            }
            var edgeCollection = edges.get(pair);
            // make sure any edge collection contains up to 1 known edge
            var containsKnownEdge =edgeCollection.stream().anyMatch(e -> e.getType().equals(EdgeType.WR) || e.getType().equals(EdgeType.SO));
            if (containsKnownEdge && (edge.getType().equals(EdgeType.WR) || edge.getType().equals(EdgeType.SO))) {
                return null;
            }
            edgeCollection.add(edge);
            return null;
        };

        BiFunction<EndpointPair<Transaction<KeyType, ValueType>>, Collection<Edge<KeyType>>, Void> addEdgeCollection = (pair, edgeCollection) -> {
            for (var e : edgeCollection) {
                addEdge.apply(pair, e);
            }
            return null;
        };

        // add edges and nodes in monoSAT conflicts to the result graph
        var handleConflicts = (BiConsumer<Pair<Collection<Pair<EndpointPair<Transaction<KeyType, ValueType>>, Collection<Edge<KeyType>>>>, Collection<SIConstraint<KeyType, ValueType>>>, EndpointPair<Transaction<KeyType, ValueType>>>) (conflicts, relateToEdge) -> {
            conflicts.getLeft().forEach(e -> {
                addEdgeCollection.apply(e.getLeft(), e.getRight());
                txns.add(e.getLeft().source());
                txns.add(e.getLeft().target());
                if (relateToEdge != null) {
                    txnRelateToMap.computeIfAbsent(e.getLeft().source(), k -> new HashSet<>()).add(relateToEdge);
                    txnRelateToMap.computeIfAbsent(e.getLeft().target(), k -> new HashSet<>()).add(relateToEdge);
                    edgeRelateToMap.computeIfAbsent(e.getKey(), k -> new HashSet<>()).add(relateToEdge);
                }
            });

//            // TODO: what if noPruning is true?
//            conflicts.getRight().forEach(c -> {
//                var addEdges = ((Consumer<Collection<SIEdge<KeyType, ValueType>>>) s -> s.forEach(e -> {
//                    txns.add(e.getFrom());
//                    txns.add(e.getTo());
//                }));
//                addEdges.accept(c.getEdges1());
//                addEdges.accept(c.getEdges2());
//            });
        };

        // get all inferred edges from conflicts
        Function<Pair<Collection<Pair<EndpointPair<Transaction<KeyType, ValueType>>, Collection<Edge<KeyType>>>>, Collection<SIConstraint<KeyType, ValueType>>>,
                Collection<Pair<EndpointPair<Transaction<KeyType, ValueType>>, Edge<KeyType>>>> getInferredEdges = conflicts -> {
            var result = new ArrayList<Pair<EndpointPair<Transaction<KeyType, ValueType>>, Edge<KeyType>>>();
            conflicts.getLeft().forEach(edgeCollection -> {
                if (edgeCollection.getRight().stream().anyMatch(e -> e.getType().equals(EdgeType.WR) || e.getType().equals(EdgeType.SO))) {
                    return;
                }
                edgeCollection.getRight().stream()
                        .filter(e -> e.getType().equals(EdgeType.WW) || e.getType().equals(EdgeType.RW))
                        .forEach(e -> result.add(Pair.of(edgeCollection.getLeft(), e)));
            });
            return result;
        };

        TriFunction<Pair<EndpointPair<Transaction<KeyType, ValueType>>, Edge<KeyType>>, Boolean, EndpointPair<Transaction<KeyType, ValueType>>, Void> handleRWEdge = (rw, addWW, relateTo) -> {
            var constraint = edgeConstraintMap.get(rw);
            Collection<SIEdge<KeyType, ValueType>> chosenEdges;
            if (constraint.getEdges1().contains(PairToSIEdge.apply(rw))) {
                chosenEdges = constraint.getEdges1();
            } else if (constraint.getEdges2().contains(PairToSIEdge.apply(rw))) {
                chosenEdges = constraint.getEdges2();
            } else {
                throw new IllegalStateException();
            }
            var wwEdges = chosenEdges.stream()
                    .filter(ww -> ww.getType().equals(EdgeType.WW))
                    .collect(Collectors.toList());
            assert wwEdges.size() == 1;
            var wwEdge = wwEdges.get(0);
            txns.add(wwEdge.getFrom());
            txns.add(rw.getLeft().source());
            addEdge.apply(EndpointPair.ordered(wwEdge.getFrom(), rw.getLeft().source()), new Edge<>(EdgeType.WR, rw.getRight().getKey()));
            if (relateTo != null) {
                txnRelateToMap.computeIfAbsent(wwEdge.getFrom(), k -> new HashSet<>()).add(relateTo);
                txnRelateToMap.computeIfAbsent(rw.getLeft().source(), k -> new HashSet<>()).add(relateTo);
                edgeRelateToMap.computeIfAbsent(EndpointPair.ordered(wwEdge.getFrom(), rw.getLeft().source()), k -> new HashSet<>()).add(relateTo);
            }
            if (addWW) {
                txns.add(wwEdge.getTo());
                addEdge.apply(EndpointPair.ordered(wwEdge.getFrom(), wwEdge.getTo()), new Edge<>(EdgeType.WW, wwEdge.getKey()));
                visited.computeIfAbsent(EndpointPair.ordered(wwEdge.getFrom(), wwEdge.getTo()), k -> new HashSet<>()).add(new Edge<>(EdgeType.WW, wwEdge.getKey()));
                if (relateTo != null) {
                    txnRelateToMap.computeIfAbsent(wwEdge.getTo(), k -> new HashSet<>()).add(relateTo);
                    edgeRelateToMap.computeIfAbsent(EndpointPair.ordered(wwEdge.getFrom(), wwEdge.getTo()), k -> new HashSet<>()).add(relateTo);
                }
            }
            if (relateTo != null) {
                txnRelateToMap.get(relateTo.source()).remove(relateTo);
                txnRelateToMap.get(relateTo.target()).remove(relateTo);
            }
            return null;
        };

        // dfs to handle inferred edges and add all edges and nodes to the result graph
        Recursive<TriFunction<KnownGraph<KeyType, ValueType>, Collection<SIConstraint<KeyType, ValueType>>, Pair<EndpointPair<Transaction<KeyType, ValueType>>, Edge<KeyType>>, Void>> dfs = new Recursive<>();
        dfs.func = (graph, constraints, e) -> {
            if (visited.containsKey(e.getKey())) {
                return null;
            }
            constraints.remove(edgeConstraintMap.get(e));
            for (var oppositeEdge : oppositeEdgesMap.get(e)) {
                graph.putEdge(oppositeEdge.getKey().nodeU(), oppositeEdge.getKey().nodeV(), oppositeEdge.getValue());
            }

            var oppositeHasConflict = false;
            for (var oppositeEdge : oppositeEdgesMap.get(e)) {
                var solver = new SISolver<>(history, graph, constraints, oppositeEdge.getKey().target(), oppositeEdge.getKey().source());
                boolean accepted = solver.solve();

                if (!accepted) {
                    oppositeHasConflict = true;
                    visited.computeIfAbsent(e.getKey(), k -> new HashSet<>()).add(e.getRight());
                    handleConflicts.accept(solver.getConflicts(), e.getKey());
                    txnRelateToMap.get(oppositeEdge.getKey().source()).remove(e.getKey());
                    txnRelateToMap.get(oppositeEdge.getKey().target()).remove(e.getKey());
                    if (e.getRight().getType().equals(EdgeType.RW)) {
                        handleRWEdge.apply(e, true, e.getKey());
                    }
                    if (oppositeEdge.getRight().getType().equals(EdgeType.RW)) {
                        handleRWEdge.apply(oppositeEdge, false, e.getKey());
                    }

                    for (var inferredEdge : getInferredEdges.apply(solver.getConflicts())) {
                        dfs.func.apply(graph, constraints, inferredEdge);
                    }
                    break;
                }
            }
            if (!oppositeHasConflict) {
                System.out.println("can not explain " + e);
                System.out.println("opposite edges do not have conflict");
                oppositeEdgesMap.get(e).forEach(opposite -> System.out.println(opposite.getKey() + " " + opposite.getRight()));
            }

            constraints.add(edgeConstraintMap.get(e));
            for (var oppositeEdge : oppositeEdgesMap.get(e)) {
                graph.removeEdge(oppositeEdge.getKey().nodeU(), oppositeEdge.getKey().nodeV(), oppositeEdge.getValue());
            }
            return null;
        };

        handleConflicts.accept(conflicts, null);
        var inferredEdgesInOriginalCycle = new LinkedList<>(getInferredEdges.apply(conflicts));
        Collections.reverse(inferredEdgesInOriginalCycle);
        for (var e : inferredEdgesInOriginalCycle) {
            dfs.func.apply(graphBeforePruning, constraintsBeforePruning, e);
        }
        edges.forEach((p, e) -> {
            e.removeIf(e1 -> (!e1.getType().equals(EdgeType.SO) && !e1.getType().equals(EdgeType.WR)) && (!visited.containsKey(p) || !visited.get(p).contains(e1)));
        });

        var edgeTypeCount = new HashMap<EdgeType, Integer>();
        conflicts.getLeft().forEach(edgeCollection -> edgeCollection.getRight().forEach(e -> edgeTypeCount.compute(e.getType(), (k, v) -> v == null ? 1 : v + 1)));

        String anomaly;
        if (edgeTypeCount.getOrDefault(EdgeType.RW, 0) > 0) {
            anomaly = "G_SI"; // G-SI cannot be parsed by graphviz
        } else if (edgeTypeCount.getOrDefault(EdgeType.SO, 0) > 0 || edgeTypeCount.getOrDefault(EdgeType.WR, 0) > 0) {
            anomaly = "G1";
        } else if (edgeTypeCount.getOrDefault(EdgeType.WW, 0) > 0) {
            anomaly = "G0";
        } else {
            throw new IllegalStateException();
        }

        var dotOutputStr = Utils.conflictsToDot(anomaly, txns, edges, txnRelateToMap, edgeRelateToMap);
        System.out.print(dotOutputStr);
        Files.writeString(Path.of(path), dotOutputStr, StandardOpenOption.CREATE);
    }

    /*
     * Generate constraints from a precedence graph. Use coalescing to reduce the
     * number of constraints produced.
     *
     * @param graph the graph to use
     *
     * @return the set of constraints generated
     *
     * For each pair of transactions A, C, generate the following constraint:
     *
     * 1. A precedes C, add A ->(ww) C. Let K be a key written by both A and C, for
     * each transaction B such that A ->(wr, K) B, add B ->(rw) C.
     *
     * 2. C precedes A, add C ->(ww) A. For each transaction B such that C ->(wr, K)
     * A, add B ->(rw) A.
     */
    private static <KeyType, ValueType> Collection<SIConstraint<KeyType, ValueType>> generateConstraintsCoalesce(
            History<KeyType, ValueType> history, KnownGraph<KeyType, ValueType> graph) {
        var readFrom = graph.getReadFrom();
        var writes = new HashMap<KeyType, Set<Transaction<KeyType, ValueType>>>();

        history.getOperations().stream().filter(e -> e.getType() == Operation.Type.WRITE).forEach(ev -> {
            writes.computeIfAbsent(ev.getKey(), k -> new HashSet<>()).add(ev.getTransaction());
        });

        var forEachWriteSameKey = ((Consumer<TriConsumer<Transaction<KeyType, ValueType>, Transaction<KeyType, ValueType>, KeyType>>) f -> {
            for (var p : writes.entrySet()) {
                var key = p.getKey();
                var list = new ArrayList<>(p.getValue());
                for (int i = 0; i < list.size(); i++) {
                    for (int j = i + 1; j < list.size(); j++) {
                        f.accept(list.get(i), list.get(j), key);
                    }
                }
            }
        });

        var constraintEdges = new HashMap<Pair<Transaction<KeyType, ValueType>, Transaction<KeyType, ValueType>>, Collection<SIEdge<KeyType, ValueType>>>();
        forEachWriteSameKey.accept((a, c, key) -> {
            var addEdge = ((BiConsumer<Transaction<KeyType, ValueType>, Transaction<KeyType, ValueType>>) (m, n) -> {
                constraintEdges.computeIfAbsent(Pair.of(m, n), p -> new ArrayList<>())
                        .add(new SIEdge<>(m, n, EdgeType.WW, key));
            });
            addEdge.accept(a, c);
            addEdge.accept(c, a);
        });

        for (var a : history.getTransactions().values()) {
            for (var b : readFrom.successors(a)) {
                for (var edge : readFrom.edgeValue(a, b).get()) {
                    for (var c : writes.get(edge.getKey())) {
                        if (a == c || b == c) {
                            continue;
                        }

                        constraintEdges.get(Pair.of(a, c)).add(new SIEdge<>(b, c, EdgeType.RW, edge.getKey()));
                    }
                }
            }
        }

        var constraints = new HashSet<SIConstraint<KeyType, ValueType>>();
        var addedPairs = new HashSet<Pair<Transaction<KeyType, ValueType>, Transaction<KeyType, ValueType>>>();
        AtomicInteger constraintId = new AtomicInteger();
        forEachWriteSameKey.accept((a, c, key) -> {
            if (addedPairs.contains(Pair.of(a, c)) || addedPairs.contains(Pair.of(c, a))) {
                return;
            }
            addedPairs.add(Pair.of(a, c));
            constraints.add(new SIConstraint<>(constraintEdges.get(Pair.of(a, c)), constraintEdges.get(Pair.of(c, a)),
                    a, c, constraintId.getAndIncrement()));
        });

        return constraints;
    }

    private static <KeyType, ValueType> Collection<SIConstraint<KeyType, ValueType>> generateConstraintsNoCoalesce(
            History<KeyType, ValueType> history, KnownGraph<KeyType, ValueType> graph) {
        var readFrom = graph.getReadFrom();
        var writes = new HashMap<KeyType, Set<Transaction<KeyType, ValueType>>>();

        history.getOperations().stream().filter(e -> e.getType() == Operation.Type.WRITE).forEach(ev -> {
            writes.computeIfAbsent(ev.getKey(), k -> new HashSet<>()).add(ev.getTransaction());
        });

        var constraints = new HashSet<SIConstraint<KeyType, ValueType>>();
        var constraintId = 0;
        for (var a : history.getTransactions().values()) {
            for (var b : readFrom.successors(a)) {
                for (var edge : readFrom.edgeValue(a, b).get()) {
                    for (var c : writes.get(edge.getKey())) {
                        if (a == c || b == c) {
                            continue;
                        }

                        constraints.add(new SIConstraint<>(
                                List.of(new SIEdge<>(a, c, EdgeType.WW, edge.getKey()),
                                        new SIEdge<>(b, c, EdgeType.RW, edge.getKey())),
                                List.of(new SIEdge<>(c, a, EdgeType.WW, edge.getKey())), a, c, constraintId++));
                    }
                }
            }
        }
        for (var write : writes.entrySet()) {
            var list = new ArrayList<>(write.getValue());
            for (int i = 0; i < list.size(); i++) {
                for (int j = i + 1; j < list.size(); j++) {
                    var a = list.get(i);
                    var c = list.get(j);
                    constraints.add(new SIConstraint<>(List.of(new SIEdge<>(a, c, EdgeType.WW, write.getKey())),
                            List.of(new SIEdge<>(c, a, EdgeType.WW, write.getKey())), a, c, constraintId++));
                }
            }
        }

        return constraints;
    }

    private static <KeyType, ValueType> Collection<SIConstraint<KeyType, ValueType>> generateConstraints(
            History<KeyType, ValueType> history, KnownGraph<KeyType, ValueType> graph) {
        if (coalesceConstraints) {
            return generateConstraintsCoalesce(history, graph);
        }
        return generateConstraintsNoCoalesce(history, graph);
    }
}
