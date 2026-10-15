package com.capstone.etp.advice;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.List;

/** The standard indicators the advice reports. Pure functions over closing prices, oldest first. */
final class Indicators {

    static final int RSI_PERIOD = 14;

    private Indicators() {
    }

    /** Simple moving average of the last {@code period} values ending at {@code endExclusive}, or null if there are not enough. */
    static BigDecimal sma(List<BigDecimal> closes, int endExclusive, int period) {
        if (period < 1 || endExclusive < period || endExclusive > closes.size()) {
            return null;
        }
        BigDecimal sum = BigDecimal.ZERO;
        for (BigDecimal value : closes.subList(endExclusive - period, endExclusive)) {
            sum = sum.add(value);
        }
        return sum.divide(BigDecimal.valueOf(period), MathContext.DECIMAL64);
    }

    /** Wilder's relative strength index (0 to 100) at the last close, or null with fewer than period + 1 closes. */
    static BigDecimal rsi(List<BigDecimal> closes, int period) {
        if (closes.size() < period + 1) {
            return null;
        }
        BigDecimal gain = BigDecimal.ZERO;
        BigDecimal loss = BigDecimal.ZERO;
        for (int i = 1; i <= period; i++) {
            BigDecimal change = closes.get(i).subtract(closes.get(i - 1));
            if (change.signum() > 0) {
                gain = gain.add(change);
            } else {
                loss = loss.add(change.negate());
            }
        }
        BigDecimal n = BigDecimal.valueOf(period);
        BigDecimal averageGain = gain.divide(n, MathContext.DECIMAL64);
        BigDecimal averageLoss = loss.divide(n, MathContext.DECIMAL64);
        for (int i = period + 1; i < closes.size(); i++) {
            BigDecimal change = closes.get(i).subtract(closes.get(i - 1));
            BigDecimal up = change.signum() > 0 ? change : BigDecimal.ZERO;
            BigDecimal down = change.signum() < 0 ? change.negate() : BigDecimal.ZERO;
            averageGain = averageGain.multiply(n.subtract(BigDecimal.ONE)).add(up).divide(n, MathContext.DECIMAL64);
            averageLoss = averageLoss.multiply(n.subtract(BigDecimal.ONE)).add(down).divide(n, MathContext.DECIMAL64);
        }
        if (averageLoss.signum() == 0) {
            return averageGain.signum() == 0 ? BigDecimal.valueOf(50) : BigDecimal.valueOf(100);
        }
        BigDecimal relativeStrength = averageGain.divide(averageLoss, MathContext.DECIMAL64);
        return BigDecimal.valueOf(100).subtract(BigDecimal.valueOf(100).divide(BigDecimal.ONE.add(relativeStrength), MathContext.DECIMAL64))
                .setScale(2, RoundingMode.HALF_UP);
    }

    /** Percentage change over the last {@code period} observations, or null. */
    static BigDecimal momentumPercent(List<BigDecimal> closes, int period) {
        if (period < 1 || closes.size() <= period) {
            return null;
        }
        BigDecimal start = closes.get(closes.size() - 1 - period);
        if (start.signum() == 0) {
            return null;
        }
        return closes.get(closes.size() - 1).subtract(start).multiply(BigDecimal.valueOf(100)).divide(start, 2, RoundingMode.HALF_UP);
    }

    /** Standard deviation of the last {@code period} one-step returns, as a percentage, or null. */
    static BigDecimal volatilityPercent(List<BigDecimal> closes, int period) {
        if (period < 2 || closes.size() <= period) {
            return null;
        }
        double[] returns = new double[period];
        for (int i = 0; i < period; i++) {
            BigDecimal previous = closes.get(closes.size() - 1 - period + i);
            BigDecimal next = closes.get(closes.size() - period + i);
            if (previous.signum() == 0) {
                return null;
            }
            returns[i] = next.subtract(previous).doubleValue() / previous.doubleValue();
        }
        double mean = 0;
        for (double value : returns) {
            mean += value;
        }
        mean /= period;
        double squares = 0;
        for (double value : returns) {
            squares += (value - mean) * (value - mean);
        }
        return BigDecimal.valueOf(Math.sqrt(squares / period) * 100).setScale(2, RoundingMode.HALF_UP);
    }
}
