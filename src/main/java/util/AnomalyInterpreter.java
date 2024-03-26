package util;

import checker.PolySI.graph.Edge;
import checker.PolySI.graph.EdgeType;
import checker.PolySI.util.Recursive;
import checker.PolySI.verifier.*;
import com.google.common.graph.EndpointPair;
import history.History;
import history.Transaction;
import org.apache.commons.lang3.tuple.Pair;

import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

public class AnomalyInterpreter {
    static <KeyType, ValueType> String interpretConflicts(Pair<Collection<Pair<EndpointPair<Transaction<KeyType, ValueType>>, Collection<Edge<KeyType>>>>, Collection<SIConstraint<KeyType, ValueType>>> conflicts) {
        // result graph
        var txns = new HashSet<Transaction<KeyType, ValueType>>();
        var edges = new HashMap<EndpointPair<Transaction<KeyType, ValueType>>, Collection<Edge<KeyType>>>();
        var adj = new HashMap<Transaction<KeyType, ValueType>, Set<Transaction<KeyType, ValueType>>>();
        var oppositeEdges = new HashMap<EndpointPair<Transaction<KeyType, ValueType>>, Collection<Edge<KeyType>>>();
        var similarEdgesMap = new HashMap<Pair<EndpointPair<Transaction<KeyType, ValueType>>, Edge<KeyType>>, Collection<Pair<EndpointPair<Transaction<KeyType, ValueType>>, Edge<KeyType>>>>();
        var oppositeEdgesMap = new HashMap<Pair<EndpointPair<Transaction<KeyType, ValueType>>, Edge<KeyType>>, Collection<Pair<EndpointPair<Transaction<KeyType, ValueType>>, Edge<KeyType>>>>();
        var txnRelateToMap = new HashMap<Transaction<KeyType, ValueType>, Collection<EndpointPair<Transaction<KeyType, ValueType>>>>();
        var edgeRelateToMap = new HashMap<EndpointPair<Transaction<KeyType, ValueType>>, Collection<EndpointPair<Transaction<KeyType, ValueType>>>>();

        List<Pair<EndpointPair<Transaction<KeyType, ValueType>>, Collection<Edge<KeyType>>>> mainCycle = new ArrayList<>();

        Function<SIEdge<KeyType, ValueType>, Pair<EndpointPair<Transaction<KeyType, ValueType>>, Edge<KeyType>>> SIEdgeToPair = e ->
                Pair.of(EndpointPair.ordered(e.getFrom(), e.getTo()), new Edge<>(e.getType(), e.getKey()));
        Function<Pair<EndpointPair<Transaction<KeyType, ValueType>>, Edge<KeyType>>, SIEdge<KeyType, ValueType>> PairToSIEdge = e -> new SIEdge<>(e.getLeft().source(), e.getLeft().target(), e.getRight().getType(), e.getRight().getKey());


        // construct opposite edges map
        for (var c : conflicts.getRight()) {
            for (var eitherEdge : c.getEdges1()) {
                similarEdgesMap.put(SIEdgeToPair.apply(eitherEdge), c.getEdges1().stream()
                        .map(SIEdgeToPair)
                        .collect(Collectors.toList()));
                oppositeEdgesMap.put(SIEdgeToPair.apply(eitherEdge), c.getEdges2().stream()
                        .map(SIEdgeToPair)
                        .collect(Collectors.toList()));
            }
            for (var orEdge : c.getEdges2()) {
                similarEdgesMap.put(SIEdgeToPair.apply(orEdge), c.getEdges2().stream()
                        .map(SIEdgeToPair)
                        .collect(Collectors.toList()));
                oppositeEdgesMap.put(SIEdgeToPair.apply(orEdge), c.getEdges1().stream()
                        .map(SIEdgeToPair)
                        .collect(Collectors.toList()));
            }
        }


        // add edges and nodes to known edge
        Function<Pair<EndpointPair<Transaction<KeyType, ValueType>>, Collection<Edge<KeyType>>>, Void> addEdgeCollectionToKnownGraph = (edge) -> {
            txns.add(edge.getLeft().source());
            txns.add(edge.getLeft().target());
            edges.computeIfAbsent(edge.getLeft(), k -> new HashSet<>()).addAll(edge.getRight());
            adj.computeIfAbsent(edge.getLeft().source(), k -> new HashSet<>()).add(edge.getLeft().target());
            return null;
        };

        Function<Pair<EndpointPair<Transaction<KeyType, ValueType>>, Edge<KeyType>>, Void> addEdgeToKnownGraph = (edge) -> {
            txns.add(edge.getLeft().source());
            txns.add(edge.getLeft().target());
            edges.computeIfAbsent(edge.getLeft(), k -> new HashSet<>()).add(edge.getRight());
            adj.computeIfAbsent(edge.getLeft().source(), k -> new HashSet<>()).add(edge.getLeft().target());
            return null;
        };

        // add edges and nodes to known edge
        Function<Pair<EndpointPair<Transaction<KeyType, ValueType>>, Edge<KeyType>>, Void> removeEdgeInKnownGraph = (edge) -> {
            edges.computeIfAbsent(edge.getLeft(), k -> new HashSet<>()).remove(edge.getRight());
            if (edges.get(edge.getLeft()).isEmpty()) {
                edges.remove(edge.getLeft());
                adj.computeIfAbsent(edge.getLeft().source(), k -> new HashSet<>()).remove(edge.getLeft().target());
            }
            if (edges.keySet().stream().noneMatch(e -> e.source().equals(edge.getLeft().source()) || e.target().equals(edge.getLeft().source()))) {
                txns.remove(edge.getLeft().source());
            }
            if (edges.keySet().stream().noneMatch(e -> e.source().equals(edge.getLeft().target()) || e.target().equals(edge.getLeft().target()))) {
                txns.remove(edge.getLeft().target());
            }
            return null;
        };

        conflicts.getLeft().forEach(addEdgeCollectionToKnownGraph::apply);
        conflicts.getRight().forEach(c -> c.getEdges1().stream().map(SIEdgeToPair).forEach(e -> {
            addEdgeCollectionToKnownGraph.apply(Pair.of(e.getLeft(), Collections.singleton(e.getRight())));
        }));

        Recursive<TriFunction<Transaction<KeyType, ValueType>, Transaction<KeyType, ValueType>, Set<Transaction<KeyType, ValueType>>, List<Pair<EndpointPair<Transaction<KeyType, ValueType>>, Collection<Edge<KeyType>>>>>> getPathEdges = new Recursive<>();
        getPathEdges.func = (source, target, visited) -> {
            if (visited.contains(source)) {
                return null;
            }
            visited.add(source);
            if (source.equals(target)) {
                return new LinkedList<>();
            }
            for (var next : adj.getOrDefault(source, new HashSet<>())) {
                var result = getPathEdges.func.apply(next, target, visited);
                if (result == null) {
                    continue;
                }
                result.add(0, Pair.of(EndpointPair.ordered(source, next), edges.get(EndpointPair.ordered(source, next))));
                return result;
            }
            return null;
        };


        Function<Void, List<List<Pair<EndpointPair<Transaction<KeyType, ValueType>>, Collection<Edge<KeyType>>>>>> checkCyclic = (ignore) -> {
            var result = new ArrayList<List<Pair<EndpointPair<Transaction<KeyType, ValueType>>, Collection<Edge<KeyType>>>>>();
            for (var e : edges.keySet()) {
                var cycle = getPathEdges.func.apply(e.target(), e.source(), new HashSet<>());
                if (cycle != null) {
                    cycle.add(Pair.of(e, edges.get(e)));
                    cycle.sort((o1, o2) -> {
                        int compare = Math.toIntExact(o1.getLeft().source().getId() - o2.getLeft().source().getId());
                        if (compare != 0) {
                            return compare;
                        }
                        return Math.toIntExact(o1.getLeft().target().getId() - o2.getLeft().target().getId());
                    });
                    if (result.stream().anyMatch(c -> c.equals(cycle))) {
                        continue;
                    }
                    result.add(cycle);
                }
            }
            return result;
        };

        var cycleList = checkCyclic.apply(null);
        if (cycleList.isEmpty()) {
            System.err.println("No cycles found!");
            return "";
        }
        mainCycle = cycleList.get(0);

        Function<List<Pair<EndpointPair<Transaction<KeyType, ValueType>>, Collection<Edge<KeyType>>>>,
                Collection<Pair<EndpointPair<Transaction<KeyType, ValueType>>, Edge<KeyType>>>> getInferredEdges = cycle -> {
            var result = new ArrayList<Pair<EndpointPair<Transaction<KeyType, ValueType>>, Edge<KeyType>>>();
            cycle.forEach(edgeCollection -> {
                if (edgeCollection.getRight().stream().anyMatch(e -> e.getType().equals(EdgeType.WR) || e.getType().equals(EdgeType.SO))) {
                    return;
                }
                edgeCollection.getRight().stream()
                        .filter(e -> e.getType().equals(EdgeType.WW) || e.getType().equals(EdgeType.RW))
                        .forEach(e -> result.add(Pair.of(edgeCollection.getLeft(), e)));
            });
            return result;
        };

        var explainedEdges = new HashSet<EndpointPair<Transaction<KeyType, ValueType>>>();
        var toExplainEdges = new ArrayList<Pair<EndpointPair<Transaction<KeyType, ValueType>>, Edge<KeyType>>>();
        for (var cycle : cycleList) {
            toExplainEdges.addAll(getInferredEdges.apply(cycle));
        }

        TriFunction<Pair<EndpointPair<Transaction<KeyType, ValueType>>, Edge<KeyType>>, Boolean, EndpointPair<Transaction<KeyType, ValueType>>, Void> handleRWEdge = (rw, addWW, relateTo) -> {
            var wwEdges = similarEdgesMap.get(rw).stream()
                    .filter(ww -> ww.getRight().getType().equals(EdgeType.WW) && ww.getRight().getKey().equals(rw.getRight().getKey()))
                    .collect(Collectors.toList());
            assert wwEdges.size() == 1;
            var wwEdge = wwEdges.get(0);

            addEdgeToKnownGraph.apply(Pair.of(EndpointPair.ordered(wwEdge.getLeft().source(), rw.getLeft().source()), new Edge<>(EdgeType.WR, rw.getRight().getKey())));
            if (relateTo != null) {
                txnRelateToMap.computeIfAbsent(wwEdge.getLeft().source(), k -> new HashSet<>()).add(relateTo);
                txnRelateToMap.computeIfAbsent(rw.getLeft().source(), k -> new HashSet<>()).add(relateTo);
                edgeRelateToMap.computeIfAbsent(EndpointPair.ordered(wwEdge.getLeft().source(), rw.getLeft().source()), k -> new HashSet<>()).add(relateTo);
            }
            if (addWW) {
                addEdgeToKnownGraph.apply(wwEdge);
                explainedEdges.add(wwEdge.getLeft());
                if (relateTo != null) {
                    txnRelateToMap.computeIfAbsent(wwEdge.getLeft().target(), k -> new HashSet<>()).add(relateTo);
                    edgeRelateToMap.computeIfAbsent(wwEdge.getLeft(), k -> new HashSet<>()).add(relateTo);
                }
            }
            if (relateTo != null) {
                txnRelateToMap.computeIfAbsent(relateTo.source(), k -> new HashSet<>()).remove(relateTo);
                txnRelateToMap.computeIfAbsent(relateTo.target(), k -> new HashSet<>()).remove(relateTo);
            }
            return null;
        };

        BiFunction<EndpointPair<Transaction<KeyType, ValueType>>, EndpointPair<Transaction<KeyType, ValueType>>, Void> addEdgeToRelateTo = (edge, relateTo) -> {
            txnRelateToMap.computeIfAbsent(edge.source(), k -> new HashSet<>()).add(relateTo);
            txnRelateToMap.computeIfAbsent(edge.target(), k -> new HashSet<>()).add(relateTo);
            edgeRelateToMap.computeIfAbsent(edge, k -> new HashSet<>()).add(relateTo);
            return null;
        };

        Recursive<BiFunction<Pair<EndpointPair<Transaction<KeyType, ValueType>>, Edge<KeyType>>, Set<EndpointPair<Transaction<KeyType, ValueType>>>, Void>> explainInducedEdge = new Recursive<>();
        explainInducedEdge.func = (edge, visited) -> {
            if (edge.getRight().getType() == EdgeType.WR || edge.getRight().getType() == EdgeType.SO) {
                return null;
            }
            if (visited.contains(edge.getLeft())) {
                return null;
            }

            similarEdgesMap.get(edge).forEach(removeEdgeInKnownGraph::apply);
            oppositeEdgesMap.get(edge).forEach(addEdgeToKnownGraph::apply);
            for (var oppositeEdge : oppositeEdgesMap.get(edge)) {
                var pathEdges = getPathEdges.func.apply(oppositeEdge.getLeft().target(), oppositeEdge.getLeft().source(), new HashSet<>());
                if (pathEdges == null) {
                    continue;
                }
                visited.add(edge.getLeft());
                oppositeEdges.computeIfAbsent(oppositeEdge.getLeft(), k -> new HashSet<>()).add(oppositeEdge.getRight());
                addEdgeToRelateTo.apply(oppositeEdge.getLeft(), edge.getLeft());
                if (edge.getRight().getType() == EdgeType.RW) {
                    handleRWEdge.apply(edge, true, edge.getLeft());
                }
                if (oppositeEdge.getRight().getType() == EdgeType.RW) {
                    handleRWEdge.apply(oppositeEdge, false, edge.getLeft());
                }

                for (var inducedEdge : getInferredEdges.apply(pathEdges)) {
                    explainInducedEdge.func.apply(inducedEdge, visited);
                }

                pathEdges.forEach(addEdgeCollectionToKnownGraph::apply);
                pathEdges.stream().map(Pair::getLeft).forEach(e -> {
                    addEdgeToRelateTo.apply(e, edge.getLeft());
                });
                txnRelateToMap.computeIfAbsent(edge.getLeft().source(), k -> new HashSet<>()).remove(edge.getLeft());
                txnRelateToMap.computeIfAbsent(edge.getLeft().target(), k -> new HashSet<>()).remove(edge.getLeft());
            }
            similarEdgesMap.get(edge).forEach(addEdgeToKnownGraph::apply);
            oppositeEdgesMap.get(edge).forEach(removeEdgeInKnownGraph::apply);
            return null;
        };


        int i = 0;
        while (!toExplainEdges.isEmpty() && ++i < 10000) {
            var edge = toExplainEdges.remove(0);
            if (explainedEdges.contains(edge.getLeft())) {
                continue;
            }
            explainInducedEdge.func.apply(edge, explainedEdges);
            if (!explainedEdges.contains(edge.getLeft())) {
                toExplainEdges.add(edge);
            }
        }

        // clean up
        edges.forEach((p, e) -> {
            e.removeIf(e1 -> (!e1.getType().equals(EdgeType.SO) && !e1.getType().equals(EdgeType.WR)) && (!explainedEdges.contains(p)));
        });
        edges.entrySet().removeIf(e -> e.getValue().isEmpty());
        txns.removeIf(t -> edges.keySet().stream().noneMatch(e -> e.source().equals(t) || e.target().equals(t)));

        var edgeTypeCount = new HashMap<EdgeType, Integer>();
        mainCycle.forEach(edgeCollection -> edgeCollection.getRight().forEach(e -> edgeTypeCount.compute(e.getType(), (k, v) -> v == null ? 1 : v + 1)));

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

        var dotOutputStr = Utils.conflictsToDot(anomaly, txns, edges, txnRelateToMap, edgeRelateToMap, oppositeEdges, mainCycle);
        System.out.print(dotOutputStr);
        return dotOutputStr;
    }

    public static <KeyType, ValueType> String interpretSER(History<KeyType, ValueType> history) {
        Pruning.setEnablePruning(false);
        var verifier = new SERVerifier<>(history);
        verifier.audit();
        Pruning.setEnablePruning(true);

        var conflicts = verifier.getConflicts();
        if (conflicts == null) {
            return "";
        }
        return interpretConflicts(conflicts);
    }

    public static <KeyType, ValueType> String interpretSI(History<KeyType, ValueType> history) {
        Pruning.setEnablePruning(false);
        var verifier = new SIVerifier<>(history);
        verifier.audit();
        Pruning.setEnablePruning(true);

        var conflicts = verifier.getConflicts();
        if (conflicts == null) {
            return "";
        }
        return interpretConflicts(conflicts);
    }
}
