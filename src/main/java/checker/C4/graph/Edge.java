package checker.C4.graph;

import lombok.Data;

@Data
public class Edge<VarType> {
    public enum Type {
        WR, SO, CO, VO
    }
    private final Type type;
    private final VarType variable;
}
