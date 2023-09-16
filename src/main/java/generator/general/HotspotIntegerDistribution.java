package generator.general;

import org.apache.commons.lang3.NotImplementedException;
import org.apache.commons.math3.distribution.AbstractIntegerDistribution;
import org.apache.commons.math3.distribution.UniformIntegerDistribution;
import org.apache.commons.math3.exception.NumberIsTooLargeException;
import org.apache.commons.math3.exception.util.LocalizedFormats;
import org.apache.commons.math3.random.RandomGenerator;
import org.apache.commons.math3.random.Well19937c;

public class HotspotIntegerDistribution extends AbstractIntegerDistribution {
    private final int lower;
    private final int upper;
    private final double hotProb;
    private final BernoulliDistribution bernoulliDistribution;
    private final UniformIntegerDistribution hotDistribution;
    private final UniformIntegerDistribution nonHotDistribution;

    public HotspotIntegerDistribution(int lower, int upper, double hotProb)
            throws NumberIsTooLargeException {
        this(new Well19937c(), lower, upper, hotProb);
    }

    public HotspotIntegerDistribution(RandomGenerator rng, int lower, int upper, double hotProb) throws NumberIsTooLargeException {
        super(rng);
        if (lower > upper) {
            throw new NumberIsTooLargeException(LocalizedFormats.LOWER_BOUND_NOT_BELOW_UPPER_BOUND, lower, upper, true);
        }
        this.lower = lower;
        this.upper = upper;
        this.hotProb = hotProb;
        int mid = (int) ((upper - lower) * hotProb + lower);
        bernoulliDistribution = new BernoulliDistribution(1 - hotProb);
        hotDistribution = new UniformIntegerDistribution(lower, mid);
        nonHotDistribution = new UniformIntegerDistribution(mid + 1, upper);
    }

    @Override
    public double probability(int x) {
        throw new NotImplementedException();
    }

    @Override
    public double cumulativeProbability(int x) {
        throw new NotImplementedException();
    }

    @Override
    public double getNumericalMean() {
        throw new NotImplementedException();
    }

    @Override
    public double getNumericalVariance() {
        throw new NotImplementedException();
    }

    @Override
    public int getSupportLowerBound() {
        return lower;
    }

    @Override
    public int getSupportUpperBound() {
        return upper;
    }

    @Override
    public boolean isSupportConnected() {
        return false;
    }

    @Override
    public int sample() {
        if (bernoulliDistribution.sample()) {
            return hotDistribution.sample();
        } else {
            return nonHotDistribution.sample();
        }
    }
}
