package history.serializer;

import com.fasterxml.jackson.databind.ObjectMapper;
import history.History;
import history.Operation;
import history.Session;
import history.Transaction;
import lombok.Data;
import lombok.SneakyThrows;
import org.apache.commons.io.FileUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class ViperHistorySerializer implements HistorySerializer<Long, Long> {

    @SneakyThrows
    private String transactionToString(Transaction<Long, Long> txn) {
        List<List<Object>> ops = new ArrayList<>();
        txn.getOps().forEach(op -> {
            ops.add(List.of(op.getType() == Operation.Type.READ ? "r" : "w", op.getKey(), op.getValue(), txn.isSuccess()));
        });
        ObjectMapper mapper = new ObjectMapper();
        return mapper.writeValueAsString(new Txn(ops)) + "\n";
    }

    private String sessionToString(Session<Long, Long> session) {
        return session.getTransactions().stream()
                .map(this::transactionToString)
                .collect(Collectors.joining());
    }

    /**
     * Serializes the given history into a JSON string and writes it to a dir.
     *
     * @param history the history object to be serialized
     * @param path    the dir of the file to write the serialized JSON string to
     */
    @Override
    @SneakyThrows
    public void serializeHistory(History<Long, Long> history, String path) {
        Path dir = Paths.get(path);
        FileUtils.deleteDirectory(dir.toFile());
        Files.createDirectories(dir);

        history.getSessions().forEach((sid, session) -> {
            String filename = String.format("J%d.log", sid + 24);
            Path file = Paths.get(path, filename);
            try {
                Files.writeString(file, sessionToString(session), StandardOpenOption.CREATE);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Data
    static class Txn {
        private final List<List<Object>> value;
    }
}
