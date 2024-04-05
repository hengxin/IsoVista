package checker.C4;

import checker.C4.graph.Edge;
import checker.C4.graph.Node;
import checker.C4.taps.TAP;
import checker.IsolationLevel;
import history.History;
import history.Operation;
import history.loader.ElleHistoryLoader;
import javafx.util.Pair;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public class C4List<VarType> extends C4<VarType, ElleHistoryLoader.ElleValue> {
    public static final String NAME = "C4-list";
    Object ZERO = new ElleHistoryLoader.ElleValue(null, new ArrayList<>());
    public C4List(Properties config) {
        super(config);
    }

    @Override
    public boolean verify(History<VarType, ElleHistoryLoader.ElleValue> history) {
        this.history = history;
        profiler.startTick(constructionTag);
        buildCO();
        constructionTime = profiler.endTick(constructionTag);
        profiler.startTick(traversalTag);
        checkCOTAP();
        if (ISOLATION_LEVEL == IsolationLevel.REPEATABLE_READ) {
            traversalTime = profiler.endTick(traversalTag);
            return tapCount.isEmpty();
        }
        syncClock();
        buildWW();
        buildCM();
        if (!hasCircle(Edge.Type.CM)) {
            traversalTime = profiler.endTick(traversalTag);
            return tapCount.isEmpty();
        }
        checkCMTAP();
        traversalTime = profiler.endTick(traversalTag);
        return tapCount.isEmpty();
    }


    @Override
    protected void buildCO() {
        var hist = history.getFlatTransactions();
        Map<Long, Node<VarType, ElleHistoryLoader.ElleValue>> prevNodes = new HashMap<>();

        for (var txn: hist) {

            // update node with prev node
            var prev = prevNodes.get(txn.getSession().getId());
            var node = constructNode(txn, prev);
            graph.addVertex(node);
            prevNodes.put(txn.getSession().getId(), node);
            if (prev != null) {
                graph.addEdge(prev, node, new Edge<>(Edge.Type.SO, null));
            }

            var nearestRW = new HashMap<VarType, Operation<VarType, ElleHistoryLoader.ElleValue>>();
            var writesInTxn = new HashMap<VarType, Operation<VarType, ElleHistoryLoader.ElleValue>>();

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

    private void buildWW() {
        Set<Node<VarType, ElleHistoryLoader.ElleValue>> pendingNodes = new HashSet<>();
        reads.values().forEach((readList) -> {
            readList.forEach((read) -> {
                var ref = new Object() {
                    Node<VarType, ElleHistoryLoader.ElleValue> prev = null;
                };
                read.getValue().getList().forEach((val) -> {
                    var key = new Pair<>(read.getKey(), new ElleHistoryLoader.ElleValue(val, null));
                    var write = writes.get(key);
                    if (write == null) {
                        return;
                    }
                    var node = op2node.get(write);
                    if (ref.prev == null) {
                        ref.prev = node;
                        pendingNodes.add(ref.prev);
                    }
                    graph.addEdge(ref.prev, node, new Edge<>(Edge.Type.CM, read.getKey()));
                    ref.prev = node;
                });
            });
        });
        pendingNodes.forEach((node) -> {
            updateVec(new HashSet<>(), node, node, Edge.Type.CM);
        });
    }

    @Override
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
                read.getValue().getList().forEach((v) -> {
                    var key = new Pair<>(read.getKey(),  new ElleHistoryLoader.ElleValue(v, null));
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
                                                // find initReadMono if read y precedes read x
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

                    if (writeNode == null) {
                        return;
                    }

                    if (!writeNode.equals(node)) {
                        // in different txn, and v should be the last element in the read list
                        if (internalWrites.contains(write) && read.getValue().getLastElement().equals(v)) {
                            // find intermediate write
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
                }
            });
        });
    }
}
