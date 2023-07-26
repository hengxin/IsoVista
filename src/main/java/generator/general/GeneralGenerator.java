package generator.general;

import generator.Generator;
import history.History;

import java.util.Properties;

public class GeneralGenerator implements Generator<Long, Long> {
    private long session;
    private long transaction;
    private long operation;
    private double readProportion;
    private long key;
    private String distribution;

    public GeneralGenerator(Properties config) {
        this.session = Long.parseLong(config.getProperty("workload.session"));
        this.transaction = Long.parseLong(config.getProperty("workload.transaction"));
        this.operation = Long.parseLong(config.getProperty("workload.operation"));
        this.readProportion = Double.parseDouble(config.getProperty("workload.readproportion"));
        this.key = Long.parseLong(config.getProperty("workload.key"));
        this.distribution = config.getProperty("workload.distribution");
    }

    @Override
    public History<Long, Long> generate() {
        return null;
    }
}
