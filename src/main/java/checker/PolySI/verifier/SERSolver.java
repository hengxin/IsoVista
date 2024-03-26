package checker.PolySI.verifier;

import checker.PolySI.graph.Edge;
import checker.PolySI.graph.EdgeType;
import checker.PolySI.graph.KnownGraph;
import checker.PolySI.graph.MatrixGraph;
import com.google.common.graph.EndpointPair;
import com.google.common.graph.MutableValueGraph;
import com.google.common.graph.ValueGraph;
import history.History;
import history.Transaction;
import monosat.Graph;
import monosat.Lit;
import monosat.Logic;
import monosat.Solver;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;
import util.Profiler;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static checker.PolySI.verifier.Utils.mergeGraph;

@SuppressWarnings("UnstableApiUsage")
class SERSolver<KeyType, ValueType> {
    private final Solver solver = new Solver();

    // The literals of the known graph
    private final Map<Lit, Pair<EndpointPair<Transaction<KeyType, ValueType>>, Collection<Edge<KeyType>>>> knownLiterals = new HashMap<>();

    private final Map<EndpointPair<Transaction<KeyType, ValueType>>, Lit> knownLiteralsReverse = new HashMap<>();

    // The literals asserting that exactly one set of edges exists in the graph
    // for each constraint
    private final Map<Lit, SIConstraint<KeyType, ValueType>> constraintLiterals = new HashMap<>();

    boolean solve() {
        var profiler = Profiler.getInstance();
        var lits = Stream
                .concat(knownLiterals.keySet().stream(),
                        constraintLiterals.keySet().stream())
                .collect(Collectors.toList());

        profiler.startTick("SI_SOLVER_SOLVE");
        var result = solver.solve(lits);
        profiler.endTick("SI_SOLVER_SOLVE");

        return result;
    }

    Pair<Collection<Pair<EndpointPair<Transaction<KeyType, ValueType>>, Collection<Edge<KeyType>>>>, Collection<SIConstraint<KeyType, ValueType>>> getConflicts() {
        var edges = new ArrayList<Pair<EndpointPair<Transaction<KeyType, ValueType>>, Collection<Edge<KeyType>>>>();
        var constraints = new ArrayList<SIConstraint<KeyType, ValueType>>();

        solver.getConflictClause().stream().map(Logic::not).forEach(lit -> {
            if (knownLiterals.containsKey(lit)) {
                edges.add(knownLiterals.get(lit));
            } else {
                constraints.add(constraintLiterals.get(lit));
            }
        });
        return Pair.of(edges, constraints);
    }

    /*
     * Construct SISolver from constraints
     *
     * First construct two graphs: 1. Graph A contains WR, WW and SO edges. 2.
     * Graph B contains RW edges.
     *
     * For each edge in A and B, create a literal for it. The edge exists in the
     * final graph iff. the literal is true.
     *
     * Then, construct a third graph C using A and B: If P -> Q in A and Q -> R
     * in B, then P -> R in C The literal of P -> R is ((P -> Q) and (Q -> R)).
     *
     * Lastly, we add graph A and C to monosat, resulting in the final graph.
     *
     * Literals that are passed as assumptions to monograph: 1. The literals of
     * WR, SO edges, because those edges always exist. 2. For each constraint, a
     * literal that asserts exactly one set of edges exist in the graph.
     */
    SERSolver(History<KeyType, ValueType> history,
              KnownGraph<KeyType, ValueType> precedenceGraph,
              Collection<SIConstraint<KeyType, ValueType>> constraints) {
        var profiler = Profiler.getInstance();

        profiler.startTick("SI_SOLVER_GEN");
        profiler.startTick("SI_SOLVER_GEN_GRAPH_A_B");
        var graphA = createKnownGraph(history,
                precedenceGraph.getKnownGraphA());
        var graphB = createKnownGraph(history,
                precedenceGraph.getKnownGraphB());
        var graph = mergeGraph(graphA, graphB);
        profiler.endTick("SI_SOLVER_GEN_GRAPH_A_B");

        profiler.startTick("SI_SOLVER_GEN_REACHABILITY");
        // The reachability information is used to delete unneeded edges from
        // the generated graph
        var mat = new MatrixGraph<>(graph.asGraph());
        var orderInSession = Utils.getOrderInSession(history);
        mat = Utils.reduceEdges(mat, orderInSession);
        var reachability = mat.reachability();
        profiler.endTick("SI_SOLVER_GEN_REACHABILITY");

        profiler.startTick("SI_SOLVER_GEN_GRAPH_A_UNION_C");
        // Known edges and unknown edges are collected separately
        var knownEdges = Utils.getKnownEdges(graph, mat);
        addConstraints(constraints, graph);
        var unknownEdges = Utils.getUnknownEdges(graph, reachability);
        profiler.endTick("SI_SOLVER_GEN_GRAPH_A_UNION_C");

        profiler.startTick("SI_SOLVER_GEN_MONO_GRAPH");
        var monoGraph = new Graph(solver);
        var nodeMap = new HashMap<Transaction<KeyType, ValueType>, Integer>();

        history.getTransactions().values().forEach(n -> {
            nodeMap.put(n, monoGraph.addNode());
        });

        var addToMonoSAT = ((Consumer<Triple<Transaction<KeyType, ValueType>, Transaction<KeyType, ValueType>, Lit>>) e -> {
            var n = e.getLeft();
            var s = e.getMiddle();
            solver.assertEqual(e.getRight(),
                    monoGraph.addEdge(nodeMap.get(n), nodeMap.get(s)));
        });

        knownEdges.forEach(addToMonoSAT);
        unknownEdges.forEach(addToMonoSAT);
        solver.assertTrue(monoGraph.acyclic());

        profiler.endTick("SI_SOLVER_GEN_MONO_GRAPH");
        profiler.endTick("SI_SOLVER_GEN");
    }

    private MutableValueGraph<Transaction<KeyType, ValueType>, Collection<Lit>> createKnownGraph(
            History<KeyType, ValueType> history,
            ValueGraph<Transaction<KeyType, ValueType>, Collection<Edge<KeyType>>> knownGraph) {
        var g = Utils.createEmptyGraph(history);
        for (var e : knownGraph.edges()) {
            if (knownLiteralsReverse.containsKey(e)) {
                var lit = knownLiteralsReverse.get(e);
                Utils.addEdge(g, e.source(), e.target(), lit);
                knownLiterals.get(lit).getRight().addAll(knownGraph.edgeValue(e).get());
                continue;
            }
            var lit = new Lit(solver);
            knownLiterals.put(lit, Pair.of(e, knownGraph.edgeValue(e).get()));
            knownLiteralsReverse.put(e, lit);
            Utils.addEdge(g, e.source(), e.target(), lit);
        }

        return g;
    }



    private void addConstraints(
            Collection<SIConstraint<KeyType, ValueType>> constraints,
            MutableValueGraph<Transaction<KeyType, ValueType>, Collection<Lit>> graph) {
        var addEdges = ((Function<Collection<SIEdge<KeyType, ValueType>>, Pair<Lit, Lit>>) edges -> {
            // all means all edges exists in the graph.
            // Similar for none.
            Lit all = Lit.True, none = Lit.True;
            for (var e : edges) {
                var lit = new Lit(solver);
                var not = Logic.not(lit);
                all = Logic.and(all, lit);
                none = Logic.and(none, not);
                solver.setDecisionLiteral(lit, false);
                solver.setDecisionLiteral(not, false);
                solver.setDecisionLiteral(all, false);
                solver.setDecisionLiteral(none, false);

                Utils.addEdge(graph, e.getFrom(), e.getTo(), lit);
            }
            return Pair.of(all, none);
        });

        for (var c : constraints) {
            var p1 = addEdges.apply(c.getEdges1());
            var p2 = addEdges.apply(c.getEdges2());

            constraintLiterals
                    .put(Logic.or(Logic.and(p1.getLeft(), p2.getRight()),
                            Logic.and(p2.getLeft(), p1.getRight())), c);
        }
    }
}
