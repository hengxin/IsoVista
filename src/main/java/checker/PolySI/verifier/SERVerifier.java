package checker.PolySI.verifier;


import checker.PolySI.graph.Edge;
import checker.PolySI.graph.EdgeType;
import checker.PolySI.graph.KnownGraph;
import checker.PolySI.util.TriConsumer;
import com.google.common.graph.EndpointPair;
import history.History;
import history.Operation;
import history.Transaction;
import lombok.Getter;
import lombok.Setter;
import lombok.SneakyThrows;
import org.apache.commons.lang3.tuple.Pair;
import util.AnomalyInterpreter;
import util.Profiler;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

@SuppressWarnings("UnstableApiUsage")
public class SERVerifier<KeyType, ValueType> {
    private final History<KeyType, ValueType> history;

    @Getter
    @Setter
    private static boolean coalesceConstraints = true;

    @Getter
    private Pair<Collection<Pair<EndpointPair<Transaction<KeyType, ValueType>>, Collection<Edge<KeyType>>>>, Collection<SIConstraint<KeyType, ValueType>>> conflicts;

    private Transaction<KeyType, ValueType> intConflict;

    @Getter
    private Map<String, Long> stageTime;

    public SERVerifier(History<KeyType, ValueType> history) {
        this.history = history;
    }

    public boolean audit() {
        var profiler = Profiler.getInstance();

        profiler.startTick("ONESHOT_CONS");
        profiler.startTick("SER_VERIFY_INT");
        var satisfy_int = Utils.verifyInternalConsistency(history);
        profiler.endTick("SER_VERIFY_INT");
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
        var constructionTime = profiler.endTick("ONESHOT_CONS");

        profiler.startTick("PRUNING");
        var hasLoop = Pruning.pruneConstraints(graph, constraints, history);
        var pruningTime = profiler.endTick("PRUNING");
        if (hasLoop) {
            System.err.printf("Cycle found in pruning\n");
        }
        System.err.printf("After Prune:\n" + "Constraints count: %d\nTotal edges in constraints: %d\n",
                constraints.size(),
                constraints.stream().map(c -> c.getEdges1().size() + c.getEdges2().size()).reduce(0, Integer::sum));

        profiler.startTick("ONESHOT_ENCODING");
        var solver = new SERSolver<>(history, graph, constraints);
        var encodeTime = profiler.endTick("ONESHOT_ENCODING");

        profiler.startTick("ONESHOT_SOLVE");
        boolean accepted = solver.solve();
        var solveTime = profiler.endTick("ONESHOT_SOLVE");

        if (!accepted) {
            conflicts = solver.getConflicts();
        }
        this.stageTime = Map.of(
                "Construction", constructionTime,
                "Pruning", pruningTime,
                "Encoding", encodeTime,
                "Solving", solveTime
        );

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

        var dotOutputStr = AnomalyInterpreter.interpretSER(history);
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
