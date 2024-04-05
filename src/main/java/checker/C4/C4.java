package checker.C4;

import checker.C4.graph.Edge;
import checker.C4.graph.Graph;
import checker.C4.graph.Node;
import checker.C4.graph.TCNode;
import checker.C4.taps.TAP;
import checker.Checker;
import checker.IsolationLevel;
import com.google.common.collect.Sets;
import config.Config;
import history.History;
import history.Operation;
import history.Transaction;
import history.loader.ElleHistoryLoader;
import javafx.util.Pair;
import lombok.Data;
import lombok.SneakyThrows;
import org.apache.commons.lang3.tuple.Triple;
import util.Profiler;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.function.Function;
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
    // triple <t1, t2, t3>
    protected final Map<Pair<Node<VarType, ValType>, Node<VarType, ValType>>, Node<VarType, ValType>> CMCauses = new HashMap<>();

    protected Object ZERO = 0L;
    protected static final Map<IsolationLevel, Set<TAP>> PROHIBITED_TAPS = new HashMap<>();

    public static final String NAME = "C4";
    protected final Profiler profiler = Profiler.getInstance();
    private final Properties config;

    public static IsolationLevel ISOLATION_LEVEL;
    protected final String constructionTag = "Construction";
    protected final String traversalTag = "Traversal";
    protected long constructionTime;
    protected long traversalTime;

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
        Set<TAP> RRTAPS = new HashSet<>(List.of(new TAP[]{
                TAP.NonRepeatableRead
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
        PROHIBITED_TAPS.put(IsolationLevel.REPEATABLE_READ, RRTAPS);
        PROHIBITED_TAPS.put(IsolationLevel.READ_ATOMICITY, RATAPs);
        PROHIBITED_TAPS.put(IsolationLevel.CAUSAL_CONSISTENCY, TCCTAPs);
    }

    public C4(Properties config) {
        this.config = config;
        ISOLATION_LEVEL = IsolationLevel.valueOf(config.getProperty(Config.CHECKER_ISOLATION));
    }

    public boolean verify(History<VarType, ValType> history) {
        if (config.getProperty(Config.HISTORY_TYPE, Config.DEFAULT_HISTORY_TYPE).equals("elle") && config.getProperty(Config.WORKLOAD_SKIP_GENERATION).equals("true")) {
            return new C4List<>(config).verify((History<Object, ElleHistoryLoader.ElleValue>) history);
        }
        this.history = history;
        profiler.startTick(constructionTag);
        buildCO();
        constructionTime = profiler.endTick(constructionTag);
        profiler.startTick(traversalTag);
        checkCOTAP();
        if (ISOLATION_LEVEL == IsolationLevel.REPEATABLE_READ) {
//            System.out.println(badPatternCount);
            traversalTime = profiler.endTick(traversalTag);
            return tapCount.isEmpty();
        }
        syncClock();
        buildCM();
        if (!hasCircle(Edge.Type.CM)) {
//            System.out.println(badPatternCount);
            traversalTime = profiler.endTick(traversalTag);
            return tapCount.isEmpty();
        }
        checkCMTAP();
        traversalTime = profiler.endTick(traversalTag);
//        System.out.println(badPatternCount);
        return tapCount.isEmpty();
    }

    @SneakyThrows
    @Override
    public void outputDotFile(String path) {
        if (bugGraphs.isEmpty()) {
            return;
        }
        Files.writeString(Path.of(path), bugGraphs.get(0), StandardOpenOption.CREATE);
    }

    @Override
    public Map<String, Long> getProfileInfo() {
        return new HashMap<>() {{
            put(constructionTag, constructionTime);
            put(traversalTag, traversalTime);
        }};
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
                        // build cm edge
//                        if (t.canReachByCO(t1)) {
//                            return;
//                        }
                        graph.addEdge(t, t1, new Edge<>(Edge.Type.CM, variable));
                        CMCauses.put(new Pair<>(t, t1), t2);
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
                            // find COConflictCM
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
                                    // find NonMonoReadCM
                                    findTAP(TAP.NonMonoReadCM, varX, t1, t2, t3);
                                } else {
                                    // find FracturedReadCM
                                    findTAP(TAP.FracturedReadCM, varX, t1, t2, t3);
                                }
                            }
                        }
                        if (!findSubTAP) {
                            // find ConflictCM
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
        int dim = Math.toIntExact(Collections.max(history.getSessions().keySet())) + 1;
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
            String builder = "digraph " +
                    tap +
                    " {\n" +
                    String.format("\"%s\" [ops=\"%s\"];\n", nodes[0].getTransaction(), nodes[0].getTransaction().getOps()) +
                    "}\n";
            bugGraphs.add(builder);
        } else {
            Map<Node<VarType, ValType>, Set<Triple<Node<VarType, ValType>, Node<VarType, ValType>, Edge<VarType>>>> nodeMap = new HashMap<>();
            Map<Triple<Node<VarType, ValType>, Node<VarType, ValType>, Edge<VarType>>, Set<Triple<Node<VarType, ValType>, Node<VarType, ValType>, Edge<VarType>>>> edgeMap = new HashMap<>();

            Function<Set<Triple<Node<VarType, ValType>, Node<VarType, ValType>, Edge<VarType>>>, String> CMListToString = (CMList) -> {
                if (CMList == null || CMList.isEmpty()) {
                    return "";
                }
                var builder = new StringBuilder();
                for (var cm : CMList) {
                    builder.append(String.format("%s -> %s, ", cm.getLeft().getTransaction(), cm.getMiddle().getTransaction()));
                }
                builder.delete(builder.length() - 2, builder.length());
                return builder.toString();
            };

            BiFunction<Pair<List<Node<VarType, ValType>>, List<Edge<VarType>>>, Triple<Node<VarType, ValType>, Node<VarType, ValType>, Edge<VarType>>, Void> addNodesAndEdges = (path, cm) -> {
                for (int i = 0; i < path.getKey().size(); ++i) {
                    nodeMap.compute(path.getKey().get(i), (k, v) -> {
                        if (v == null) {
                            v = new HashSet<>();
                        }
                        if (cm != null && !cm.getLeft().equals(k) && !cm.getMiddle().equals(k)) {
                            v.add(cm);
                        }
                        return v;
                    });
                    if (i >= path.getKey().size() - 1) {
                        continue;
                    }
                    edgeMap.compute(Triple.of(path.getKey().get(i), path.getKey().get(i + 1), path.getValue().get(i)), (k, v) -> {
                        if (v == null) {
                            v = new HashSet<>();
                        }
                        if (cm != null && !cm.equals(k)) {
                            v.add(cm);
                        }
                        return v;
                    });
                }
                return null;
            };

            var CMEdge = Triple.of(nodes[1], nodes[0], new Edge<VarType>(Edge.Type.CM, varX));
            edgeMap.put(CMEdge, null);
            edgeMap.put(Triple.of(nodes[0], nodes[2], new Edge<>(Edge.Type.WR, varX)), Sets.newHashSet(CMEdge));

            Pair<List<Node<VarType, ValType>>, List<Edge<VarType>>> pathFromT1ToT2 = null;
            if (tap.equals(TAP.NonMonoReadCO) || tap.equals(TAP.FracturedReadCO) || tap.equals(TAP.COConflictCM)) {
                pathFromT1ToT2 = path(nodes[0], nodes[1], Sets.newHashSet(Edge.Type.SO, Edge.Type.WR));
            } else if (tap.equals(TAP.NonMonoReadCM) || tap.equals(TAP.FracturedReadCM) || tap.equals(TAP.ConflictCM)) {
                pathFromT1ToT2 = path(nodes[0], nodes[1], Sets.newHashSet(Edge.Type.SO, Edge.Type.WR, Edge.Type.CM));
            }
            addNodesAndEdges.apply(pathFromT1ToT2, null);

            Pair<List<Node<VarType, ValType>>, List<Edge<VarType>>> pathFromT2ToT3;
            if (tap.equals(TAP.NonMonoReadCO) || tap.equals(TAP.NonMonoReadCM)) {
                pathFromT2ToT3 = path(nodes[1], nodes[2], Sets.newHashSet(Edge.Type.WR));
            } else {
                pathFromT2ToT3 = path(nodes[1], nodes[2], Sets.newHashSet(Edge.Type.SO, Edge.Type.WR));
            }
            addNodesAndEdges.apply(pathFromT2ToT3, CMEdge);

            var CMList = edgeMap.keySet().stream()
                    .filter(tri -> tri.getRight().getType().equals(Edge.Type.CM))
                    .collect(Collectors.toList());
            while (!CMList.isEmpty()) {
                var cm = CMList.remove(0);
                if (cm.equals(CMEdge)) {
                    continue;
                }

                var t1 = cm.getMiddle();
                var t2 = cm.getLeft();
                var t3 = CMCauses.get(new Pair<>(t2, t1));

                var WRPath = path(t1, t3, Sets.newHashSet(Edge.Type.WR));
                var COPath = path(t2, t3, Sets.newHashSet(Edge.Type.SO, Edge.Type.WR));
                addNodesAndEdges.apply(WRPath, cm);
                addNodesAndEdges.apply(COPath, cm);
            }

            var builder = new StringBuilder();
            builder.append("digraph ");
            builder.append(tap);
            builder.append(" {\n");
            for (var e : nodeMap.entrySet()) {
                builder.append(String.format("\"%s\" [id=\"%s\" ops=\"%s\" relate_to=\"%s\"];\n",
                        e.getKey().getTransaction(), e.getKey().getTransaction(), e.getKey().getTransaction().getOps(),
                        CMListToString.apply(e.getValue())));
            }
            for (var e : edgeMap.entrySet()) {
                builder.append(String.format("\"%s\" -> \"%s\" [id=\"%s -> %s\" label=\"%s\" relate_to=\"%s\"];\n",
                        e.getKey().getLeft().getTransaction(), e.getKey().getMiddle().getTransaction(),
                        e.getKey().getLeft().getTransaction(), e.getKey().getMiddle().getTransaction(),
                        e.getKey().getRight().toString(), CMListToString.apply(e.getValue())));
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
