package history;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Operation<KeyType, ValType> {
    public enum Type {
        READ,
        WRITE
    }

    @EqualsAndHashCode.Include
    private final Type type;

    @EqualsAndHashCode.Include
    private final KeyType key;

    @EqualsAndHashCode.Include
    private final ValType value;

    @EqualsAndHashCode.Include
    private final Transaction<KeyType, ValType> transaction;

    @EqualsAndHashCode.Include
    private final Integer id;
}
