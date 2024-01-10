package checker.C4;

import checker.C4.graph.Edge;
import checker.C4.graph.Graph;
import checker.C4.graph.Node;
import checker.C4.graph.TCNode;
import checker.C4.taps.TAP;
import checker.Checker;
import checker.IsolationLevel;
import config.Config;
import history.History;
import history.Operation;
import history.Transaction;
import javafx.util.Pair;
import lombok.Data;
import lombok.SneakyThrows;
import org.apache.commons.lang3.tuple.Triple;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;


@Data
public class C4<VarType, ValType> implements Checker<VarType, ValType> {
    protected History<VarType, ValType> history;

    protected final Set<TAP> taps = new HashSet<>();
    protected final Map<String, Integer> tapCount = new HashMap<>();
    protected final List<String> bugGraphs = new ArrayList<>();
    protected final Graph<VarType, ValType> graph = new Graph<>();

    protected final Map<Pair<VarType, ValType>, Operation<VarType, ValType>> writes = new HashMap<>();
    protected final Map<Pair<VarType, ValType>, List<Operation<VarType, ValType>>> reads = new HashMap<>();
    protected final Map<Pair<VarType, ValType>, List<Operation<VarType, ValType>>> readsWithoutWrites = new HashMap<>();
    protected final Map<VarType, Set<Node<VarType, ValType>>> writeNodes = new HashMap<>();
    protected final Map<VarType, Set<Pair<Node<VarType, ValType>, Node<VarType, ValType>>>> WREdges = new HashMap<>();
    protected final Map<Pair<Node<VarType, ValType>, Node<VarType, ValType>>, List<Pair<Operation<VarType, ValType>, Operation<VarType, ValType>>>> WRNodesToOp = new HashMap<>();
    protected final Map<Operation<VarType, ValType>, Node<VarType, ValType>> op2node = new HashMap<>();
    protected final Set<Operation<VarType, ValType>> internalWrites = new HashSet<>();

    protected Object ZERO = 0L;
    protected static final Map<IsolationLevel, Set<TAP>> PROHIBITED_TAPS = new HashMap<>();

    public static final String NAME = "C4";
    public static IsolationLevel ISOLATION_LEVEL;

    static {
        Set<TAP> RCTAPs = new HashSet<>(List.of(new TAP[]{
                TAP.ThinAirRead,
                TAP.AbortedRead,
                TAP.FutureRead,
                TAP.NotMyOwnWrite,
                TAP.NotMyLastWrite,
                TAP.IntermediateRead,
                TAP.CyclicCO,
                TAP.NonMonoReadCO,
                TAP.NonMonoReadCM,
        }));
        Set<TAP> RATAPs = new HashSet<>(RCTAPs);
        RATAPs.addAll(List.of(new TAP[]{
                TAP.NonRepeatableRead,
                TAP.FracturedReadCO,
                TAP.FracturedReadCM
        }));
        Set<TAP> TCCTAPs = new HashSet<>(RATAPs);
        TCCTAPs.addAll(List.of(new TAP[]{
                TAP.COConflictCM,
                TAP.ConflictCM
        }));
        PROHIBITED_TAPS.put(IsolationLevel.READ_COMMITTED, RCTAPs);
        PROHIBITED_TAPS.put(IsolationLevel.READ_ATOMICITY, RATAPs);
        PROHIBITED_TAPS.put(IsolationLevel.CAUSAL_CONSISTENCY, TCCTAPs);
    }

    public C4(Properties config) {
        ISOLATION_LEVEL = IsolationLevel.valueOf(config.getProperty(Config.CHECKER_ISOLATION));
        assert ISOLATION_LEVEL == IsolationLevel.SNAPSHOT_ISOLATION;
    }

    public boolean verify(History<VarType, ValType> history) {
        this.history = history;
        buildCO();
        checkCOTAP();
        if (ISOLATION_LEVEL == IsolationLevel.READ_COMMITTED) {
//            System.out.println(badPatternCount);
            return tapCount.isEmpty();
        }
        syncClock();
        buildCM();
        if (!hasCircle(Edge.Type.CM)) {
//            System.out.println(badPatternCount);
            return tapCount.isEmpty();
        }
        checkCMTAP();
//        System.out.println(badPatternCount);
        return tapCount.isEmpty();
    }

    @SneakyThrows
    @Override
    public void outputDotFile(String path) {
        Files.writeString(Path.of(path), bugGraphs.get(0), StandardOpenOption.CREATE);
    }

    protected void buildCO() {
        var hist = history.getFlatTransactions();
        Map<Long, Node<VarType, ValType>> prevNodes = new HashMap<>();

        for (var txn: hist) {

            // update node with prev node
            var prev = prevNodes.get(txn.getSession().getId());
            var node = constructNode(txn, prev);
            graph.addVertex(node);
            prevNodes.put(txn.getSession().getId(), node);
            if (prev != null) {
                graph.addEdge(prev, node, new Edge<>(Edge.Type.SO, null));
            }

            var nearestRW = new HashMap<VarType, Operation<VarType, ValType>>();
            var writesInTxn = new HashMap<VarType, Operation<VarType, ValType>>();

            for (var op: txn.getOps()) {
                var key = new Pair<>(op.getKey(), op.getValue());
                op2node.put(op, node);

                // if op is a read
                if (op.getType() == Operation.Type.READ) {

                    // check NonRepeatableRead and NotMyOwnWrite
                    var prevRW = nearestRW.get(op.getKey());
                    if (prevRW != null && !op.getValue().equals(prevRW.getValue())) {
                        if (prevRW.getType() == Operation.Type.READ) {
                            findTAP(TAP.NonRepeatableRead, node);
                        } else {
                            boolean findNotMyLastWrite = false;
                            for (var prevOp: txn.getOps()) {
                                if (prevOp.getId() < prevRW.getId() &&
                                        prevOp.getType() == Operation.Type.WRITE &&
                                        prevOp.getKey().equals(op.getKey()) &&
                                        prevOp.getValue().equals(op.getValue())
                                ) {
                                    findNotMyLastWrite = true;
                                    findTAP(TAP.NotMyLastWrite, node);
                                }
                            }
                            if (!findNotMyLastWrite) {
                                findTAP(TAP.NotMyOwnWrite, node);
                            }
                        }
                    }
                    nearestRW.put(op.getKey(), op);

                    var write = writes.get(key);
                    if (write != null) {
                        // if write -> op
                        // add op to reads
                        reads.computeIfAbsent(key, k -> new ArrayList<>()).add(op);

                        var writeNode = op2node.get(write);
                        if (!writeNode.equals(node)) {
                            if (!writeNode.canReachByCO(node)) {
                                node.updateCOReachability(writeNode);
                            }
                            graph.addEdge(writeNode, node, new Edge<>(Edge.Type.WR, op.getKey()));
                            WREdges.computeIfAbsent(op.getKey(), k -> new HashSet<>()).add(new Pair<>(writeNode, node));
                            WRNodesToOp.computeIfAbsent(new Pair<>(writeNode, node), wr -> new ArrayList<>()).add(new Pair<>(write, op));
                        }
                    } else if (op.getValue().equals(ZERO)) {
                        // if no write -> op, but op reads zero
                        reads.computeIfAbsent(key, k -> new ArrayList<>()).add(op);
                    } else {
                        readsWithoutWrites.computeIfAbsent(key, k -> new ArrayList<>()).add(op);
                    }
                } else {
                    // if op is a write
                    if (op.getValue().equals(ZERO)) {
                        // ignore write 0
                        continue;
                    }
                    writes.put(key, op);
                    writeNodes.computeIfAbsent(op.getKey(), k -> new HashSet<>()).add(node);

                    nearestRW.put(op.getKey(), op);

                    // check internal write
                    var internalWrite = writesInTxn.get(op.getKey());
                    if (internalWrite != null) {
                        internalWrites.add(internalWrite);
                    }
                    writesInTxn.put(op.getKey(), op);

                    var pendingReads = readsWithoutWrites.get(key);
                    if (pendingReads != null) {
                        reads.computeIfAbsent(key, k -> new ArrayList<>()).addAll(pendingReads);
                        for (var pendingRead: pendingReads) {
                            var pendingReadNode = op2node.get(pendingRead);
                            if (!node.equals(pendingReadNode)) {
                                graph.addEdge(node, pendingReadNode, new Edge<>(Edge.Type.WR, op.getKey()));
                                WREdges.computeIfAbsent(op.getKey(), k -> new HashSet<>()).add(new Pair<>(node, pendingReadNode));
                                WRNodesToOp.computeIfAbsent(new Pair<>(node, pendingReadNode), wr -> new ArrayList<>()).add(new Pair<>(op, pendingRead));
                            }
                        }
                    }
                    readsWithoutWrites.remove(key);
                }
            }
            updateVec(new HashSet<>(), node, node, Edge.Type.CO);
        }
    }

    protected void checkCOTAP() {
        // check aborted read and thin air
        if (readsWithoutWrites.size() > 0) {
            AtomicInteger count = new AtomicInteger();
            readsWithoutWrites.keySet().forEach((key) -> {
                if (history.getAbortedWrites().contains(key)) {
                    // find aborted read
                    findTAP(TAP.AbortedRead);
                    count.addAndGet(1);
                }
            });
            if (count.get() != readsWithoutWrites.size()) {
                // find thin air read
                findTAP(TAP.ThinAirRead);
            }
        }

        // for each read
        reads.values().forEach((readList) -> {
            readList.forEach((read) -> {
                var key = new Pair<>(read.getKey(), read.getValue());
                var node = op2node.get(read);

                // read(x, 0)
                if (read.getValue().equals(ZERO)) {
                    var writeRelNodes = writeNodes.get(read.getKey());

                    // no write(x, k)
                    if (writeRelNodes == null) {
                        return;
                    }

                    // check if write(x, k) co-> read
                    writeRelNodes.forEach((writeNode) -> {
                        if (writeNode.equals(node)) {
                            return;
                        }
                        if (writeNode.canReachByCO(node)) {
                            // there are 3 cases: initReadMono initReadWR or writeCOInitRead
                            boolean findSubTap = false;
                            for (var writeY : writeNode.getTransaction().getOps()) {
                                for (var readY : node.getTransaction().getOps()) {
                                    if (!writeY.getKey().equals(read.getKey()) &&
                                            writeY.getType().equals(Operation.Type.WRITE) &&
                                            readY.getType().equals(Operation.Type.READ) &&
                                            writeY.getKey().equals(readY.getKey()) &&
                                            writeY.getValue().equals(readY.getValue())) {
                                        // find w(y, v_y) wr-> r(y, v_y)
                                        findSubTap = true;
                                        if (readY.getId() < read.getId()) {
                                            // find nonMonoReadCO  if read y precedes read x
                                            findTAP(TAP.NonMonoReadCO);
                                        } else {
                                            // find initReadWR
                                            findTAP(TAP.FracturedReadCO);
                                        }
                                    }
                                }
                            }
                            if (!findSubTap) {
                                // find initReadCO if not InitReadMono or InitReadWR
                                findTAP(TAP.COConflictCM);
                            }
                        }
                    });
                    return;
                }

                // write wr-> read
                var write = writes.get(key);
                var writeNode = op2node.get(write);

                if (!writeNode.equals(node)) {
                    // in different txn
                    if (internalWrites.contains(write)) {
                        // find intermediate write
                        // TODO: viz 2 txn tap
                        findTAP(TAP.IntermediateRead, writeNode);
                    }
                } else {
                    // in same txn
                    if (write.getId() > read.getId()) {
                        // find future read
                        findTAP(TAP.FutureRead, node);
                    }
                }
            });
        });

        // check CyclicCO
        // iter wr edge (t1 wr-> t2)
        WREdges.forEach((varX, edgesX) -> {
            edgesX.forEach((edge) -> {
                var t1 = edge.getKey();
                var t2 = edge.getValue();
                if (t1.canReachByCO(t2) && t2.canReachByCO(t1)) {
                    // find cyclicCO
                    findTAP(TAP.CyclicCO);
//                    print2TxnBp(t1, t2);
                }
            });
        });
    }

    protected void buildCM() {
        var pendingNodes = new HashSet<Node<VarType, ValType>>();

        WREdges.forEach((variable, edges) -> {
            edges.forEach((edge) -> {
                var t1 = edge.getKey();
                var t2 = edge.getValue();
                writeNodes.get(variable).forEach((t) -> {
                    if (!t.equals(t1) && !(t.equals(t2)) && t.canReachByCO(t2)) {
                        // build ao edge
                        if (t.canReachByCO(t1)) {
                            return;
                        }
                        graph.addEdge(t, t1, new Edge<>(Edge.Type.CM, null));
                        pendingNodes.add(t);
                    }
                });
            });
        });

        // update downstream nodes
        pendingNodes.forEach((node) -> {
            updateVec(new HashSet<>(), node, node, Edge.Type.CM);
        });
    }

    protected void checkCMTAP() {
        // iter wr edge (t1 wr-> t3)
        WRNodesToOp.forEach((WRNodePair, WROpPairList) -> {
            var t1 = WRNodePair.getKey();
            var t3 = WRNodePair.getValue();
            WROpPairList.forEach((WROpPair) -> {
                var varX = WROpPair.getKey().getKey();
                writeNodes.get(varX).forEach((t2) -> {
                    if (!t2.equals(t1) && !t2.equals(t3) && t2.canReachByCO(t3) && t1.canReachByCO(t2)) {
                        // find tap triangle
                        boolean findSubTAP = false;
                        var edges = graph.getEdge(t2, t3);
                        if (edges != null) {
                            for (var edge: edges) {
                                if (edge.getType() == Edge.Type.SO) {
                                    findTAP(TAP.FracturedReadCO, varX, t1, t2, t3);
                                    findSubTAP = true;
                                }
                            }
                        }
                        if (WRNodesToOp.containsKey(new Pair<>(t2, t3))) {
                            findSubTAP = true;
                            var WRYOpPairList = WRNodesToOp.get(new Pair<>(t2, t3));
                            for (var WRYOpPair : WRYOpPairList) {
                                var readY = WRYOpPair.getValue();
                                var varY = readY.getKey();
                                if (varY == varX) {
                                    continue;
                                }
                                if (readY.getId() < WROpPair.getValue().getId()) {
                                    // find NonMonoReadCO
                                    findTAP(TAP.NonMonoReadCO, varX, t1, t2, t3);
                                } else {
                                    // find FracturedReadCO
                                    findTAP(TAP.FracturedReadCO, varX, t1, t2, t3);
                                }
                            }
                        }
                        if (!findSubTAP) {
                            // find COConflictAO
                            findTAP(TAP.COConflictCM, varX, t1, t2, t3);
                        }
                    }
                    if (!t2.equals(t1) && !t2.equals(t3) && t2.canReachByCO(t3) && !t1.canReachByCO(t2) && t1.canReachByCM(t2)) {
                        // find tap triangle
                        boolean findSubTAP = false;
                        var edges = graph.getEdge(t2, t3);
                        if (edges != null) {
                            for (var edge: edges) {
                                if (edge.getType() == Edge.Type.SO) {
                                    findTAP(TAP.FracturedReadCM, varX, t1, t2, t3);
                                    findSubTAP = true;
                                }
                            }
                        }
                        if (WRNodesToOp.containsKey(new Pair<>(t2, t3))) {
                            findSubTAP = true;
                            var WRYOpPairList = WRNodesToOp.get(new Pair<>(t2, t3));
                            for (var WRYOpPair : WRYOpPairList) {
                                var readY = WRYOpPair.getValue();
                                if (readY.getId() < WROpPair.getValue().getId()) {
                                    // find NonMonoReadAO
                                    findTAP(TAP.NonMonoReadCM, varX, t1, t2, t3);
                                } else {
                                    // find FracturedReadAO
                                    findTAP(TAP.FracturedReadCM, varX, t1, t2, t3);
                                }
                            }
                        }
                        if (!findSubTAP) {
                            // find ConflictAO
                            findTAP(TAP.ConflictCM, varX, t1, t2, t3);
                        }
                    }
                });
            });
        });
    }

    protected void updateVec(Set<Node<VarType, ValType>> visited, Node<VarType, ValType> cur, Node<VarType, ValType> upNode, Edge.Type edgeType) {
        visited.add(cur);

        var nextNodes = graph.get(cur);
        for (var next: nextNodes) {
            if (edgeType == Edge.Type.CO) {
                if (visited.contains(next) || upNode.canReachByCO(next)) {
                    continue;
                }
                next.updateCOReachability(upNode);
                updateVec(visited, next, upNode, edgeType);
            } else if (edgeType == Edge.Type.CM) {
                if (visited.contains(next) || upNode.canReachByCM(next)) {
                    continue;
                }
                next.updateCMReachability(upNode);
                updateVec(visited, next, upNode, edgeType);
            }
        }
    }

    protected void findTAP(TAP tap) {
        if (!PROHIBITED_TAPS.get(ISOLATION_LEVEL).contains(tap)) {
            return;
        }
        taps.add(tap);
        tapCount.merge(tap.getCode(), 1, Integer::sum);
    }

    protected void findTAP(TAP tap, Node<VarType, ValType> node) {
        findTAP(tap);
        vizTap(tap, null, node);
    }

    @SafeVarargs
    protected final void findTAP(TAP tap, VarType varX, Node<VarType, ValType>... nodes) {
        findTAP(tap);
        vizTap(tap, varX, nodes);
    }

    protected Node<VarType, ValType> constructNode(Transaction<VarType, ValType> transaction, Node<VarType, ValType> prev) {
        short tid = (short) transaction.getSession().getId();
        int dim = history.getSessions().size();
        return new TCNode<>(graph, transaction, tid, dim, prev);
    }

    protected void syncClock() {
        graph.getAdjMap().keySet().forEach(Node::syncCOCM);
    }

    protected boolean hasCircle(Edge.Type edgeType) {
        return graph.getAdjMap().entrySet().stream().anyMatch((entry) -> {
            var from = entry.getKey();
            var toNodes = entry.getValue();
            return toNodes.stream().anyMatch((node) -> (edgeType == Edge.Type.CO && node.canReachByCO(from)) ||
                    (edgeType == Edge.Type.CM && node.canReachByCM(from)));
        });
    }

    @SafeVarargs
    protected final void vizTap(TAP tap, VarType varX, Node<VarType, ValType>... nodes) {
        if (nodes.length < 1) {
            // do nothing
        } else if (nodes.length == 1) {
            var builder = new StringBuilder();
            builder.append("digraph {\n");
            builder.append(String.format("\"%s\" [ops=\"%s\"];\n", nodes[0].getTransaction(), nodes[0].getTransaction().getOps()));
            builder.append("}\n");
            bugGraphs.add(builder.toString());
        } else {
            Set<Node<VarType, ValType>> nodeSet = new HashSet<>(Arrays.asList(nodes));
            Set<Triple<Node<VarType, ValType>, Node<VarType, ValType>, Edge<VarType>>> edgeSet = new HashSet<>();

            Pair<List<Node<VarType, ValType>>, List<Edge<VarType>>> pathFromT1ToT2 = null;
            if (tap.equals(TAP.NonMonoReadCO) || tap.equals(TAP.FracturedReadCO) || tap.equals(TAP.COConflictCM)) {
                pathFromT1ToT2 = path(nodes[0], nodes[1], Set.of(Edge.Type.SO, Edge.Type.WR));
            } else if (tap.equals(TAP.NonMonoReadCM) || tap.equals(TAP.FracturedReadCM) || tap.equals(TAP.ConflictCM)) {
                pathFromT1ToT2 = path(nodes[0], nodes[1], Set.of(Edge.Type.SO, Edge.Type.WR, Edge.Type.CM));
            }
            for (int i = 0; i < pathFromT1ToT2.getKey().size() - 1; ++i) {
                nodeSet.add(pathFromT1ToT2.getKey().get(i));
                edgeSet.add(Triple.of(pathFromT1ToT2.getKey().get(i), pathFromT1ToT2.getKey().get(i + 1), pathFromT1ToT2.getValue().get(i)));
            }

            Pair<List<Node<VarType, ValType>>, List<Edge<VarType>>> pathFromT2ToT3;
            if (tap.equals(TAP.NonMonoReadCO) || tap.equals(TAP.NonMonoReadCM)) {
                pathFromT2ToT3 = path(nodes[1], nodes[2], Set.of(Edge.Type.WR));
            } else {
                pathFromT2ToT3 = path(nodes[1], nodes[2], Set.of(Edge.Type.SO, Edge.Type.WR));
            }
            for (int i = 0; i < pathFromT2ToT3.getKey().size() - 1; ++i) {
                nodeSet.add(pathFromT2ToT3.getKey().get(i));
                edgeSet.add(Triple.of(pathFromT2ToT3.getKey().get(i), pathFromT2ToT3.getKey().get(i + 1), pathFromT2ToT3.getValue().get(i)));
            }

            edgeSet.add(Triple.of(nodes[1], nodes[0], new Edge<>(Edge.Type.CM, null)));
            edgeSet.add(Triple.of(nodes[0], nodes[2], new Edge<>(Edge.Type.WR, varX)));

            var builder = new StringBuilder();
            builder.append("digraph {\n");
            for (var n : nodeSet) {
                builder.append(String.format("\"%s\" [ops=\"%s\"];\n", n.getTransaction(), n.getTransaction().getOps()));
            }
            for (var e : edgeSet) {
                builder.append(String.format("\"%s\" -> \"%s\" [label=\"%s\"];\n", e.getLeft().getTransaction(), e.getMiddle().getTransaction(), e.getRight()));
            }
            builder.append("}\n");
            bugGraphs.add(builder.toString());
        }
    }

    private Pair<List<Node<VarType, ValType>>, List<Edge<VarType>>> path(Node<VarType, ValType> from, Node<VarType, ValType> to, Set<Edge.Type> edgeTypes) {
        List<Node<VarType, ValType>> queue = new LinkedList<>();
        Map<Node<VarType, ValType>, Node<VarType, ValType>> parentNodeMap = new HashMap<>();
        Map<Node<VarType, ValType>, Edge<VarType>> parentEdgeMap = new HashMap<>();
        queue.add(from);
        parentNodeMap.put(from, null);
        do {
            var node = queue.remove(0);
            if (node.equals(to)) {
                break;
            }
            for (var next : graph.get(node)) {
                var edges = graph.getEdge(node, next).stream()
                        .filter(e -> edgeTypes.contains(e.getType()))
                        .collect(Collectors.toList());
                if (edges.isEmpty()) {
                    continue;
                }
                if (!parentNodeMap.containsKey(next)) {
                    queue.add(next);
                    parentNodeMap.put(next, node);
                    parentEdgeMap.put(next, edges.get(0));
                }
            }
        } while (!queue.isEmpty());
        if (!parentNodeMap.containsKey(to)) {
            return null;
        }

        List<Node<VarType, ValType>> nodes = new ArrayList<>();
        List<Edge<VarType>> edges = new ArrayList<>();
        Node<VarType, ValType> node = to;
        while (node != null) {
            var parentNode = parentNodeMap.get(node);
            var edge = parentEdgeMap.get(node);
            nodes.add(node);
            if (parentNode != null) {
                edges.add(edge);
            }
            node = parentNode;
        }

        Collections.reverse(nodes);
        Collections.reverse(edges);
        return new Pair<>(nodes, edges);
    }
}
