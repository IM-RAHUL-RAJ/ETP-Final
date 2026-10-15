package com.capstone.etp.automation;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Bollinger bands.
 * <pre>
 * middle = moving average over the period
 * upper  = middle + multiplier x standard deviation (population)
 * lower  = middle - multiplier x standard deviation
 *
 * BUY  : price crosses below the lower band  (previous price &gt;= previous lower band AND price &lt; lower band)
 * SELL : price crosses above the upper band  (previous price &lt;= previous upper band AND price &gt; upper band)
 * </pre>
 * Each band is calculated over the window that ends at the observation it is compared with. Only the crossing
 * triggers: while the price stays outside the band, later ticks do nothing.
 */
public final class BollingerBandStrategy implements TradingStrategy {

    private final int period;
    private final BigDecimal multiplier;

    public BollingerBandStrategy(int period, BigDecimal multiplier) {
        if (period < 2) {
            throw new IllegalArgumentException("period must be at least 2");
        }
        if (multiplier == null || multiplier.signum() <= 0) {
            throw new IllegalArgumentException("multiplier must be above zero");
        }
        this.period = period;
        this.multiplier = multiplier;
    }

    @Override
    public String name() {
        return "BOLLINGER_BANDS(" + period + "," + multiplier.stripTrailingZeros().toPlainString() + ")";
    }

    @Override
    public int requiredObservations() {
        return period + 1;
    }

    @Override
    public StrategySignal evaluate(List<BigDecimal> prices) {
        int size = prices == null ? 0 : prices.size();
        if (size < requiredObservations()) {
            return StrategySignal.none("insufficient history: " + size + " of " + requiredObservations() + " observations");
        }
        BigDecimal price = prices.get(size - 1);
        BigDecimal previousPrice = prices.get(size - 2);
        Band previous = band(prices, size - 1);
        Band current = band(prices, size);

        String figures = "price " + show(previousPrice) + " -> " + show(price)
                + ", lower band " + show(previous.lower) + " -> " + show(current.lower)
                + ", upper band " + show(previous.upper) + " -> " + show(current.upper);
        if (previousPrice.compareTo(previous.lower) >= 0 && price.compareTo(current.lower) < 0) {
            return new StrategySignal(SignalType.BUY, "price crossed below the lower band (" + figures + ")");
        }
        if (previousPrice.compareTo(previous.upper) <= 0 && price.compareTo(current.upper) > 0) {
            return new StrategySignal(SignalType.SELL, "price crossed above the upper band (" + figures + ")");
        }
        return StrategySignal.none("no band crossing (" + figures + ")");
    }

    private Band band(List<BigDecimal> prices, int endExclusive) {
        BigDecimal middle = Averages.mean(prices, endExclusive, period);
        BigDecimal spread = Averages.standardDeviation(prices, endExclusive, period, middle).multiply(multiplier);
        return new Band(middle.subtract(spread), middle.add(spread));
    }

    private static String show(BigDecimal value) {
        return value.setScale(4, RoundingMode.HALF_UP).toPlainString();
    }

    private record Band(BigDecimal lower, BigDecimal upper) {
    }
}
