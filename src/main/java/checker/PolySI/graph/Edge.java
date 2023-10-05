package checker.PolySI.graph;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class Edge<KeyType> {
    private final EdgeType type;
    private final KeyType key;

    @Override
    public String toString() {
        return String.format("(%s, %s)", type, key);
    }
}
