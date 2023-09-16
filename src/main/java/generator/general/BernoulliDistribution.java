package generator.general;

import java.util.Random;

public class BernoulliDistribution {
    private final Random random;
    private final double successProbability;

    public BernoulliDistribution(double successProbability) {
        if (successProbability < 0.0 || successProbability > 1.0) {
            throw new IllegalArgumentException("Invalid probability: Probability must be between 0 and 1");
        }
        this.successProbability = successProbability;
        this.random = new Random();
    }

    public boolean sample() {
        return random.nextDouble() < successProbability;
    }
}