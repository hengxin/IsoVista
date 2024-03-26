import history.History;
import history.Operation;
import history.loader.HistoryLoader;
import lombok.AllArgsConstructor;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@AllArgsConstructor
public class TestLoader implements HistoryLoader<String, Integer> {
	final Set<Integer> sessions;
	final Map<Integer, List<Integer>> transactions;
	final Map<Integer, List<Triple<Operation.Type, String, Integer>>> events;


	@Override
	public History<String, Integer> loadHistory(String path) {
		var history = new History<String, Integer>();
		sessions.forEach(history::addSession);
		transactions.forEach((sess, txns) -> txns.forEach(t -> history.addTransaction(history.getSession(sess), t)));
		events.forEach((txn, ops) -> ops.forEach(op -> history.addOperation(history.getTransaction(txn), op.getLeft(), op.getMiddle(), op.getRight())));
		return history;
	}
}
