package checker.PolySI.verifier;

import checker.PolySI.graph.EdgeType;
import history.Transaction;
import lombok.Data;

@Data
class SIEdge<KeyType, ValueType> {
    private final Transaction<KeyType, ValueType> from;
    private final Transaction<KeyType, ValueType> to;
    private final EdgeType type;
    private final KeyType key;

    @Override
    public String toString() {
        return String.format("(%s -> %s, %s, %s)", from, to, type, key);
    }
}
