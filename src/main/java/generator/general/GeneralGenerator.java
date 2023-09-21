package generator.general;

import generator.Generator;
import history.History;
import history.Operation;
import lombok.SneakyThrows;
import org.apache.commons.math3.distribution.IntegerDistribution;
import org.apache.commons.math3.distribution.UniformIntegerDistribution;
import org.apache.commons.math3.distribution.ZipfDistribution;

import java.util.ArrayList;
import java.util.Properties;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class GeneralGenerator implements Generator<Long, Long> {
    private final long session;
    private final long transaction;
    private final long operation;
    private final double readProportion;
    private final long key;
    private IntegerDistribution keyDistribution;
    private final BernoulliDistribution readProbability;

    public GeneralGenerator(Properties config) {
        this.session = Long.parseLong(config.getProperty("workload.session"));
        this.transaction = Long.parseLong(config.getProperty("workload.transaction"));
        this.operation = Long.parseLong(config.getProperty("workload.operation"));
        this.readProportion = Double.parseDouble(config.getProperty("workload.readproportion"));
        this.key = Long.parseLong(config.getProperty("workload.key"));
        var distribution = config.getProperty("workload.distribution");
        switch (distribution) {
            case "uniform":
                this.keyDistribution = new UniformIntegerDistribution(1, (int) key);
                break;
            case "zipf":
                this.keyDistribution = new ZipfDistribution((int) key, 1.5);
                break;
            case "hotspot":
                this.keyDistribution = new HotspotIntegerDistribution(1, (int) key, 0.2);
        }
        this.readProbability = new BernoulliDistribution(readProportion);
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
                        if (readProbability.sample()) {
                            type = Operation.Type.READ;
                        } else {
                            type = Operation.Type.WRITE;
                        }
                        long var = keyDistribution.sample();
                        long val = 0L;
                        if (type == Operation.Type.WRITE) {
                            val = counts.getOrDefault(var, 0L);
                            counts.put(var, val + 1);
                        }
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
