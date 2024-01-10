package checker.C4.graph;

import javafx.util.Pair;
import lombok.Data;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

@Data
public class Graph<VarType, ValType> {
    private final Map<Node<VarType, ValType>, List<Node<VarType, ValType>>> adjMap = new HashMap<>();

    private final Map<Pair<Node<VarType, ValType>, Node<VarType, ValType>>, List<Edge<VarType>>> edges = new HashMap<>();

    public void addVertex(Node<VarType, ValType> node) {
        adjMap.put(node, new LinkedList<>());
    }

    public void addEdge(Node<VarType, ValType> src, Node<VarType, ValType> dest, Edge<VarType> edge){
        var destSet = adjMap.get(src);
        if (destSet == null) {
            throw new RuntimeException();
        }
        destSet.add(dest);
        edges.computeIfAbsent(new Pair<>(src, dest), k -> new LinkedList<>()).add(edge);
    }

    public List<Node<VarType, ValType>> get(Node<VarType, ValType> node) {
        return adjMap.get(node);
    }

    public List<Edge<VarType>> getEdge(Node<VarType, ValType> src, Node<VarType, ValType> dest) {
        return edges.get(new Pair<>(src, dest));
    }
}
