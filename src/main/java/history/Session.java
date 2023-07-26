package history;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.ArrayList;
import java.util.List;

@Data
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Session<KeyType, ValType> {
    @EqualsAndHashCode.Include
    final long id;

    private final List<Transaction<KeyType, ValType>> transactions = new ArrayList<>();
}
