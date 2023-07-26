package history.loader;

import history.History;
import history.Operation;
import lombok.AllArgsConstructor;
import lombok.SneakyThrows;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.HashMap;
import java.util.regex.Pattern;

@AllArgsConstructor
public class TextHistoryLoader implements HistoryLoader<Long, Long> {
    private static final Pattern regex = Pattern.compile("([rw])\\((\\d++),(\\d++),(\\d++),(-?\\d++)\\)");

    @Override
    @SneakyThrows
    public History<Long, Long> loadHistory(String path) {
        var in = new BufferedReader(new FileReader(path));
        var history = new History<Long, Long>();
        var sessionIdMap = new HashMap<Long, Long>();
        final long[] minSessionId = {0L};

        in.lines().forEachOrdered((line) -> {
            var match = regex.matcher(line);
            if (!match.matches()) {
                throw new Error("Invalid format");
            }

            var op = match.group(1);
            var key = Long.parseLong(match.group(2));
            var value = Long.parseLong(match.group(3));
            var session = Long.parseLong(match.group(4));
            if (sessionIdMap.containsKey(session)) {
                session = sessionIdMap.get(session);
            } else {
                sessionIdMap.put(session, minSessionId[0]);
                session = minSessionId[0];
                minSessionId[0]++;
            }
            var txn = Long.parseLong(match.group(5));

            // txn == -1 => aborted
            if (txn == -1) {
                if (op.equals("w")) {
                    history.addAbortedWrite(key, value);
                }
                return;
            }

            if (history.getSession(session) == null) {
                history.addSession(session);
            }

            if (history.getTransaction(txn) == null) {
                history.addTransaction(history.getSession(session), txn);
            }

            history.addOperation(history.getTransaction(txn),
                    op.equals("r") ? Operation.Type.READ : Operation.Type.WRITE, key,
                    value);
        });

        return history;
    }
}
