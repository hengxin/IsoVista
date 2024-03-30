package checker.C4.graph;

import lombok.Data;

@Data
public class Edge<VarType> {
    public enum Type {
        WR, SO, CO, CM
    }
    private final Type type;
    private final VarType variable;

    public String toString() {
        if (variable == null) {
            return type.toString();
        }
        if (type == Type.CM) {
            return String.format("WW %s", variable);
        }
        return String.format("%s %s", type, variable);
    }
}
