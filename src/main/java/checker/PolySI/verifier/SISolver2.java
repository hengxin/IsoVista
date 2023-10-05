package checker.PolySI.verifier;

import checker.PolySI.graph.Edge;
import checker.PolySI.graph.EdgeType;
import checker.PolySI.graph.KnownGraph;
import checker.PolySI.graph.MatrixGraph;
import com.google.common.graph.EndpointPair;
import com.google.common.graph.Graph;
import history.History;
import history.Transaction;
import monosat.Lit;
import monosat.Logic;
import monosat.Solver;
import org.apache.commons.lang3.tuple.Pair;
import util.Profiler;

import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

class SISolver2<KeyType, ValueType> {
    private Lit[][] graphABEdges;
    private Lit[][] graphACEdges;

    private Solver solver = new Solver();

    private HashSet<Lit> knownLits = new HashSet<>();
    private HashSet<Lit> constraintLits = new HashSet<>();

    SISolver2(History<KeyType, ValueType> history,
            KnownGraph<KeyType, ValueType> precedenceGraph,
            Collection<SIConstraint<KeyType, ValueType>> constraints) {
        var profiler = Profiler.getInstance();
        profiler.startTick("SI_SOLVER2_CONSTRUCT");

        var nodeMap = new HashMap<Transaction<KeyType, ValueType>, Integer>();
        {
            int i = 0;
            for (var txn : history.getTransactions().values()) {
                nodeMap.put(txn, i++);
            }
        }

        var createLitMatrix = ((Function<Supplier<Lit>, Lit[][]>) newLit -> {
            var n = history.getTransactions().size();
            var lits = new Lit[n][n];
            for (int j = 0; j < n; j++) {
                for (int k = 0; k < n; k++) {
                    lits[j][k] = newLit.get();
                }
            }

            return lits;
        });
        graphABEdges = createLitMatrix.apply(() -> {
            var lit = new Lit(solver);
            solver.setDecisionLiteral(lit, false);
            return lit;
        });
        graphACEdges = createLitMatrix.apply(() -> Lit.False);

        for (var e : precedenceGraph.getKnownGraphA().edges()) {
            knownLits.add(graphABEdges[nodeMap.get(e.source())][nodeMap
                    .get(e.target())]);
        }
        for (var e : precedenceGraph.getKnownGraphB().edges()) {
            knownLits.add(graphABEdges[nodeMap.get(e.source())][nodeMap
                    .get(e.target())]);
        }

        var impliesCNF = ((BiFunction<Lit, Collection<SIEdge<KeyType, ValueType>>, Lit>) (
                lit, edges) -> edges.stream()
                        .filter(e -> e.getType().equals(EdgeType.RW))
                        .map(e -> graphABEdges[nodeMap.get(e.getFrom())][nodeMap
                                .get(e.getTo())])
                        .map(l -> Logic.or(Logic.not(lit), l))
                        .reduce(Lit.True, Logic::and));
        for (var c : constraints) {
            var i = nodeMap.get(c.getWriteTransaction1());
            var j = nodeMap.get(c.getWriteTransaction2());
            // var either = impliesCNF.apply(graphABEdges[i][j], c.getEdges1());
            // var or = impliesCNF.apply(graphABEdges[j][i], c.getEdges2());
            var either = Logic.implies(graphABEdges[i][j],
                    c.getEdges1().stream()
                            .filter(e -> e.getType().equals(EdgeType.RW))
                            .map(e -> graphABEdges[nodeMap
                                    .get(e.getFrom())][nodeMap.get(e.getTo())])
                            .reduce(Lit.True, Logic::and));
            var or = Logic.implies(graphABEdges[j][i],
                    c.getEdges2().stream()
                            .filter(e -> e.getType().equals(EdgeType.RW))
                            .map(e -> graphABEdges[nodeMap
                                    .get(e.getFrom())][nodeMap.get(e.getTo())])
                            .reduce(Lit.True, Logic::and));

            constraintLits.add(either);
            constraintLits.add(or);
            constraintLits
                    .add(Logic.xor(graphABEdges[i][j], graphABEdges[j][i]));
            solver.setDecisionLiteral(graphABEdges[i][j], true);
            solver.setDecisionLiteral(graphABEdges[j][i], true);
        }

        var matA = new MatrixGraph<>(
                precedenceGraph.getKnownGraphA().asGraph());
        var orderInSession = Utils.getOrderInSession(history);
        var minimalAUnionC = Utils.reduceEdges(
                matA.union(matA.composition(new MatrixGraph<>(
                        precedenceGraph.getKnownGraphB().asGraph()))),
                orderInSession);
        var reachability = minimalAUnionC.reachability();
        var collectEdges = ((BiFunction<Graph<Transaction<KeyType, ValueType>>, EdgeType, List<Pair<Transaction<KeyType, ValueType>, Transaction<KeyType, ValueType>>>>) (
                known, type) -> Stream
                        .concat(known.edges().stream()
                                .map(e -> Pair.of(e.source(), e.target())),
                                constraints.stream()
                                        .flatMap(c -> Stream.concat(
                                                c.getEdges1().stream(),
                                                c.getEdges2().stream()))
                                        .filter(e -> e.getType().equals(type))
                                        .map(e -> Pair.of(e.getFrom(),
                                                e.getTo())))
                        .collect(Collectors.toList()));
        var edgesInA = collectEdges
                .apply(precedenceGraph.getKnownGraphA().asGraph(), EdgeType.WW);
        var edgesInB = collectEdges
                .apply(precedenceGraph.getKnownGraphB().asGraph(), EdgeType.RW);
        for (var e1 : edgesInA) {
            var vi = nodeMap.get(e1.getLeft());
            var vj = nodeMap.get(e1.getRight());
            graphACEdges[vi][vj] = Logic.or(graphACEdges[vi][vj],
                    graphABEdges[vi][vj]);
            solver.setDecisionLiteral(graphACEdges[vi][vj], false);
        }
        for (var e1 : edgesInA) {
            for (var e2 : edgesInB) {
                if (!e1.getRight().equals(e2.getLeft())) {
                    continue;
                }

                var vi = nodeMap.get(e1.getLeft());
                var vj = nodeMap.get(e1.getRight());
                var vk = nodeMap.get(e2.getRight());
                graphACEdges[vi][vk] = Logic.or(graphACEdges[vi][vk],
                        Logic.and(graphABEdges[vi][vj], graphABEdges[vj][vk]));
                solver.setDecisionLiteral(graphACEdges[vi][vk], false);
            }
        }

        var monoGraph = new monosat.Graph(solver);
        var nodes = new int[graphACEdges.length];
        for (int i = 0; i < graphACEdges.length; i++) {
            nodes[i] = monoGraph.addNode();
        }
        for (int i = 0; i < graphACEdges.length; i++) {
            for (int j = 0; j < graphACEdges[i].length; j++) {
                var lit = monoGraph.addEdge(nodes[i], nodes[j]);
                solver.assertEqual(lit, graphACEdges[i][j]);
            }
        }

        solver.assertTrue(monoGraph.acyclic());

        profiler.endTick("SI_SOLVER2_CONSTRUCT");
    }

    public boolean solve() {
        var lits = Stream.concat(knownLits.stream(), constraintLits.stream())
                .collect(Collectors.toList());

        return solver.solve(lits);
    }

    Pair<Collection<Pair<EndpointPair<Transaction<KeyType, ValueType>>, Collection<Edge<KeyType>>>>, Collection<SIConstraint<KeyType, ValueType>>> getConflicts() {
        return Pair.of(Collections.emptyList(), Collections.emptyList());
    }
}
