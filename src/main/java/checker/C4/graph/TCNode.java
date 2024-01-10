package checker.C4.graph;

import history.Transaction;
import checker.C4.util.TreeClock;

public class TCNode<VarType, ValType> extends Node<VarType, ValType>{
    private final TreeClock clock;
    private TreeClock clockCM;

    public TCNode(Graph<VarType, ValType> graph, Transaction<VarType, ValType> transaction, short tid, int dim, Node<VarType, ValType> prev) {
        super(graph, transaction);
        this.clock = new TreeClock(tid, dim);
        if (prev != null) {
            this.clock.join(((TCNode<VarType, ValType>) prev).clock);
        }
        this.clock.incrementBy(1);
    }

    @Override
    public boolean canReachByCO(Node<VarType, ValType> other) {
        if (!(other instanceof TCNode)) {
            throw new RuntimeException("Type mismatch");
        }
        return this.clock.isLessThanOrEqual(((TCNode<VarType, ValType>) other).clock);
    }

    @Override
    public boolean canReachByCM(Node<VarType, ValType> other) {
        if (!(other instanceof TCNode)) {
            throw new RuntimeException("Type mismatch");
        }
        return this.clockCM.isLessThanOrEqual(((TCNode<VarType, ValType>) other).clockCM);
    }

    @Override
    public void updateCOReachability(Node<VarType, ValType> other) {
        if (!(other instanceof TCNode)) {
            throw new RuntimeException("Type mismatch");
        }
        this.clock.join(((TCNode<VarType, ValType>) other).clock);
    }

    @Override
    public void updateCMReachability(Node<VarType, ValType> other) {
        if (!(other instanceof TCNode)) {
            throw new RuntimeException("Type mismatch");
        }
        this.clockCM.join(((TCNode<VarType, ValType>) other).clockCM);
    }

    @Override
    public void syncCOCM() {
        clockCM = new TreeClock(clock);
    }
}
