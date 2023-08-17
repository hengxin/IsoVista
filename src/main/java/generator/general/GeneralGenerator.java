package generator.general;

import generator.Generator;
import history.History;
import history.Operation;
import lombok.SneakyThrows;
import org.apache.commons.lang3.RandomUtils;

import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class GeneralGenerator implements Generator<Long, Long> {
    private long session;
    private long transaction;
    private long operation;
    private double readProportion;
    private long key;
    private String distribution;
    private Random random;

    public GeneralGenerator(Properties config)  {
        this.session = Long.parseLong(config.getProperty("workload.session"));
        this.transaction = Long.parseLong(config.getProperty("workload.transaction"));
        this.operation = Long.parseLong(config.getProperty("workload.operation"));
        this.readProportion = Double.parseDouble(config.getProperty("workload.readproportion"));
        this.key = Long.parseLong(config.getProperty("workload.key"));
        this.distribution = config.getProperty("workload.distribution");
        this.random = new Random();
    }

    @SneakyThrows
    @Override
    public History<Long, Long> generate() {
        var history = new History<Long, Long>();
        var counts = new ConcurrentHashMap<Long, Long>();
        ExecutorService executor = Executors.newFixedThreadPool((int) this.session);
        var todo = new ArrayList<Callable<Void>>();
        for (var iSession = 0; iSession < this.session; iSession++) {
            int finalISession = iSession;
            history.addSession(iSession);
            Callable<Void> task = () -> {
                for (var iTxn = 0; iTxn < transaction; iTxn++) {
                    var txnId = finalISession * transaction + iTxn;
                    synchronized (history) {
                        history.addTransaction(history.getSession(finalISession), txnId);
                    }
                    for (var iOp = 0; iOp < operation; iOp++) {
                        Operation.Type type;
                        if (random.nextDouble() < readProportion) {
                            type = Operation.Type.READ;
                        } else {
                            type = Operation.Type.WRITE;
                        }
                        // TODO: impl distribution
                        var var = RandomUtils.nextLong(1, key + 1);
                        var val = counts.getOrDefault(var, 0L);
                        counts.put(var, val + 1);
                        synchronized (history) {
                            history.addOperation(history.getTransaction(txnId), type, var, val);
                        }
                    }
                }
                return null;
            };
            todo.add(task);
        }
        executor.invokeAll(todo);
        return history;
    }
}
