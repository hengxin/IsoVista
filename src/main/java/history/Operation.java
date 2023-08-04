package history;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@AllArgsConstructor
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
    private ValType value;

    @EqualsAndHashCode.Include
    private final Transaction<KeyType, ValType> transaction;

    @EqualsAndHashCode.Include
    private final Integer id;
}
