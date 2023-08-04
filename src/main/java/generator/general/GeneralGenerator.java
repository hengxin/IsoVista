package generator.general;

import generator.Generator;
import history.History;
import history.Operation;
import org.apache.commons.lang3.RandomUtils;

import java.util.HashMap;
import java.util.Properties;
import java.util.Random;

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

    @Override
    public History<Long, Long> generate() {
        var history = new History<Long, Long>();
        var counts = new HashMap<Long, Long>();
        for (var iSession = 0; iSession < this.session; iSession++) {
            history.addSession(iSession);
            for (var iTxn = 0; iTxn < this.transaction; iTxn++) {
                var txnId = iSession * this.transaction + iTxn;
                history.addTransaction(history.getSession(iSession), txnId);
                for (var iOp = 0; iOp < this.operation; iOp++) {
                    Operation.Type type;
                    if (random.nextDouble() < this.readProportion) {
                        type = Operation.Type.READ;
                    } else {
                        type = Operation.Type.WRITE;
                    }
                    // TODO: impl distribution
                    var key = RandomUtils.nextLong(1, this.key + 1);
                    var val = counts.getOrDefault(key, 0L);
                    counts.put(key, val + 1);
                    history.addOperation(history.getTransaction(txnId), type, key, val);
                }
            }
        }

        return history;
    }
}
