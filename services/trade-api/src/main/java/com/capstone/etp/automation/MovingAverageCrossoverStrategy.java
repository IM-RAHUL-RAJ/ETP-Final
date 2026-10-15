package com.capstone.etp.automation;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Moving-average crossover.
 * <pre>
 * BUY  : previous short MA &lt;= previous long MA  AND  current short MA &gt; current long MA
 * SELL : previous short MA &gt;= previous long MA  AND  current short MA &lt; current long MA
 * </pre>
 * Only the transition triggers. "Short MA above long MA" on its own does nothing, so a crossover that has already
 * happened does not trigger again on the ticks that follow it.
 */
public final class MovingAverageCrossoverStrategy implements TradingStrategy {

    private final int shortPeriod;
    private final int longPeriod;

    public MovingAverageCrossoverStrategy(int shortPeriod, int longPeriod) {
        if (shortPeriod < 1 || longPeriod <= shortPeriod) {
            throw new IllegalArgumentException("shortPeriod must be at least 1 and below longPeriod");
        }
        this.shortPeriod = shortPeriod;
        this.longPeriod = longPeriod;
    }

    @Override
    public String name() {
        return "MA_CROSSOVER(" + shortPeriod + "," + longPeriod + ")";
    }

    @Override
    public int requiredObservations() {
        // The long average over the previous observations and over the current ones: one more than the long period.
        return longPeriod + 1;
    }

    @Override
    public StrategySignal evaluate(List<BigDecimal> prices) {
        int size = prices == null ? 0 : prices.size();
        if (size < requiredObservations()) {
            return StrategySignal.none("insufficient history: " + size + " of " + requiredObservations() + " observations");
        }
        BigDecimal previousShort = Averages.mean(prices, size - 1, shortPeriod);
        BigDecimal previousLong = Averages.mean(prices, size - 1, longPeriod);
        BigDecimal currentShort = Averages.mean(prices, size, shortPeriod);
        BigDecimal currentLong = Averages.mean(prices, size, longPeriod);

        String figures = "short MA " + show(previousShort) + " -> " + show(currentShort)
                + ", long MA " + show(previousLong) + " -> " + show(currentLong);
        if (previousShort.compareTo(previousLong) <= 0 && currentShort.compareTo(currentLong) > 0) {
            return new StrategySignal(SignalType.BUY, "short MA crossed above long MA (" + figures + ")");
        }
        if (previousShort.compareTo(previousLong) >= 0 && currentShort.compareTo(currentLong) < 0) {
            return new StrategySignal(SignalType.SELL, "short MA crossed below long MA (" + figures + ")");
        }
        return StrategySignal.none("no crossover (" + figures + ")");
    }

    private static String show(BigDecimal value) {
        return value.setScale(4, RoundingMode.HALF_UP).toPlainString();
    }
}
