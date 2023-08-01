package checker.C4.graph;

import history.Transaction;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Data
@ToString(onlyExplicitlyIncluded = true)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public abstract class Node<VarType, ValType> {
    private final Graph<VarType, ValType> graph;

    @EqualsAndHashCode.Include
    @ToString.Include
    private final Transaction<VarType, ValType> transaction;

    public abstract boolean canReachByCO(Node<VarType, ValType> other);

    public abstract boolean canReachByVO(Node<VarType, ValType> other);

    public abstract void updateCOReachability(Node<VarType, ValType> other);

    public abstract void updateVOReachability(Node<VarType, ValType> other);

    public abstract void syncCOVO();

}