package com.capstone.etp.automation;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.List;

/** The two calculations the strategies share: a simple moving average and a population standard deviation. */
final class Averages {

    private Averages() {
    }

    /** The mean of the {@code period} prices that end just before {@code endExclusive}. */
    static BigDecimal mean(List<BigDecimal> prices, int endExclusive, int period) {
        BigDecimal sum = BigDecimal.ZERO;
        for (BigDecimal price : prices.subList(endExclusive - period, endExclusive)) {
            sum = sum.add(price);
        }
        return sum.divide(BigDecimal.valueOf(period), MathContext.DECIMAL64);
    }

    static BigDecimal standardDeviation(List<BigDecimal> prices, int endExclusive, int period, BigDecimal mean) {
        BigDecimal squares = BigDecimal.ZERO;
        for (BigDecimal price : prices.subList(endExclusive - period, endExclusive)) {
            BigDecimal difference = price.subtract(mean);
            squares = squares.add(difference.multiply(difference));
        }
        return squares.divide(BigDecimal.valueOf(period), MathContext.DECIMAL64).sqrt(MathContext.DECIMAL64);
    }
}
