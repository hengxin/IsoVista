package checker.PolySI.verifier;

import history.Transaction;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.util.Collection;

@Data
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
class SIConstraint<KeyType, ValueType> {

    // writeTransaction1 -> writeTransaction2
    @ToString.Include(rank = 1)
    private final Collection<SIEdge<KeyType, ValueType>> edges1;

    // writeTransaction2 -> writeTransaction1
    @ToString.Include(rank = 0)
    private final Collection<SIEdge<KeyType, ValueType>> edges2;

    @ToString.Include(rank = 3)
    private final Transaction<KeyType, ValueType> writeTransaction1;

    @ToString.Include(rank = 2)
    private final Transaction<KeyType, ValueType> writeTransaction2;

    @EqualsAndHashCode.Include
    private final int id;
}
