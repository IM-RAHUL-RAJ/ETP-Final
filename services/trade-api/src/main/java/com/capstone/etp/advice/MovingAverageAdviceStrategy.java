package com.capstone.etp.advice;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Moving-average rules.
 * <pre>
 * short-term trend positive: latest close is above the short moving average
 * long-term trend positive:  the short moving average is above the long moving average
 *
 * BUY  : both trends positive
 * SELL : both trends negative (latest close below the short average, short average below the long average)
 * HOLD : enough data, but neither of the above, or a BUY that is overbought / a SELL that is oversold (see below)
 * INSUFFICIENT_DATA : fewer than longPeriod closes
 * </pre>
 * The RSI (14) can hold a signal back when it is computable: a BUY with RSI at 70 or more is overbought and becomes HOLD,
 * a SELL with RSI at 30 or less is oversold and becomes HOLD. The reasoning says so.
 * The recent price movement (change over the short window) is reported for context; it does not decide the signal.
 */
@Component
public class MovingAverageAdviceStrategy implements AdviceStrategy {

    private static final BigDecimal OVERBOUGHT = BigDecimal.valueOf(70);
    private static final BigDecimal OVERSOLD = BigDecimal.valueOf(30);
    private static final int CHART_POINTS = 90;

    private final int shortPeriod;
    private final int longPeriod;

    public MovingAverageAdviceStrategy(@Value("${app.advice.short-period:10}") int shortPeriod,
                                       @Value("${app.advice.long-period:50}") int longPeriod) {
        if (shortPeriod < 1 || longPeriod <= shortPeriod) {
            throw new IllegalArgumentException("app.advice: short-period must be at least 1 and below long-period");
        }
        this.shortPeriod = shortPeriod;
        this.longPeriod = longPeriod;
    }

    @Override
    public int requiredObservations() {
        return longPeriod;
    }

    @Override
    public TradeAdvice evaluate(String symbol, List<BigDecimal> closes, Instant now) {
        int available = closes == null ? 0 : closes.size();
        if (available < longPeriod) {
            return new TradeAdvice(symbol, AdviceSignal.INSUFFICIENT_DATA, List.of(
                    "Signal unavailable: insufficient historical price data.",
                    "Required: " + longPeriod + " observations.",
                    "Available: " + available + "."), now);
        }

        BigDecimal price = closes.get(available - 1);
        BigDecimal shortAverage = average(closes, shortPeriod);
        BigDecimal longAverage = average(closes, longPeriod);
        boolean priceAbove = price.compareTo(shortAverage) > 0;
        boolean priceBelow = price.compareTo(shortAverage) < 0;
        boolean shortAbove = shortAverage.compareTo(longAverage) > 0;
        boolean shortBelow = shortAverage.compareTo(longAverage) < 0;

        List<String> reasoning = new ArrayList<>();
        reasoning.add("Latest close " + show(price) + " is " + relation(price, shortAverage) + " the "
                + shortPeriod + "-observation moving average " + show(shortAverage));
        reasoning.add("The " + shortPeriod + "-observation moving average " + show(shortAverage) + " is "
                + relation(shortAverage, longAverage) + " the " + longPeriod + "-observation moving average "
                + show(longAverage));
        BigDecimal start = closes.get(available - shortPeriod);
        if (start.signum() != 0) {
            BigDecimal movement = price.subtract(start).multiply(BigDecimal.valueOf(100))
                    .divide(start, 2, RoundingMode.HALF_UP);
            reasoning.add("Price moved " + (movement.signum() > 0 ? "+" : "") + movement.toPlainString()
                    + "% over the last " + shortPeriod + " observations (context only)");
        }

        AdviceSignal signal;
        if (priceAbove && shortAbove) {
            signal = AdviceSignal.BUY;
            reasoning.add("BUY: the short-term and the long-term trend are both positive");
        } else if (priceBelow && shortBelow) {
            signal = AdviceSignal.SELL;
            reasoning.add("SELL: the short-term and the long-term trend are both negative");
        } else {
            signal = AdviceSignal.HOLD;
            reasoning.add("HOLD: the trends do not agree, so neither the BUY nor the SELL condition is met");
        }
        BigDecimal rsi = Indicators.rsi(closes, Indicators.RSI_PERIOD);
        if (rsi != null) {
            reasoning.add("RSI (" + Indicators.RSI_PERIOD + ") is " + rsi.toPlainString() + ": " + rsiState(rsi).toLowerCase(Locale.ROOT));
            if (signal == AdviceSignal.BUY && rsi.compareTo(OVERBOUGHT) >= 0) {
                signal = AdviceSignal.HOLD;
                reasoning.add("HOLD instead of BUY: the price has risen fast (RSI " + rsi.toPlainString() + " is overbought), so a BUY is held back");
            } else if (signal == AdviceSignal.SELL && rsi.compareTo(OVERSOLD) <= 0) {
                signal = AdviceSignal.HOLD;
                reasoning.add("HOLD instead of SELL: the price has fallen fast (RSI " + rsi.toPlainString() + " is oversold), so a SELL is held back");
            }
        }
        return new TradeAdvice(symbol, signal, List.copyOf(reasoning), now);
    }

    @Override
    public AdviceMarket market(List<PricePoint> history) {
        if (history == null || history.isEmpty()) {
            return AdviceMarket.NONE;
        }
        List<BigDecimal> closes = history.stream().map(PricePoint::close).toList();
        int size = closes.size();
        BigDecimal price = closes.get(size - 1);
        BigDecimal shortAverage = Indicators.sma(closes, size, shortPeriod);
        BigDecimal longAverage = Indicators.sma(closes, size, longPeriod);
        BigDecimal rsi = Indicators.rsi(closes, Indicators.RSI_PERIOD);
        MarketTrend trend = null;
        if (shortAverage != null && longAverage != null) {
            trend = price.compareTo(shortAverage) > 0 && shortAverage.compareTo(longAverage) > 0 ? MarketTrend.BULLISH
                    : price.compareTo(shortAverage) < 0 && shortAverage.compareTo(longAverage) < 0 ? MarketTrend.BEARISH : MarketTrend.NEUTRAL;
        }
        AdviceIndicators indicators = new AdviceIndicators(price, scale(shortAverage), scale(longAverage), shortPeriod, longPeriod,
                rsi, rsi == null ? null : rsiState(rsi), Indicators.momentumPercent(closes, shortPeriod),
                Indicators.volatilityPercent(closes, Math.min(20, size - 1)), trend);

        List<ChartPoint> chart = new ArrayList<>();
        for (int i = Math.max(0, size - CHART_POINTS); i < size; i++) {
            chart.add(new ChartPoint(history.get(i).at(), closes.get(i), scale(Indicators.sma(closes, i + 1, shortPeriod)),
                    scale(Indicators.sma(closes, i + 1, longPeriod))));
        }
        return new AdviceMarket(indicators, List.copyOf(chart));
    }

    private static String rsiState(BigDecimal rsi) {
        return rsi.compareTo(OVERBOUGHT) >= 0 ? "OVERBOUGHT" : rsi.compareTo(OVERSOLD) <= 0 ? "OVERSOLD" : "NORMAL";
    }

    private static BigDecimal scale(BigDecimal value) {
        return value == null ? null : value.setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal average(List<BigDecimal> closes, int period) {
        BigDecimal sum = BigDecimal.ZERO;
        for (BigDecimal value : closes.subList(closes.size() - period, closes.size())) {
            sum = sum.add(value);
        }
        return sum.divide(BigDecimal.valueOf(period), MathContext.DECIMAL64);
    }

    private static String relation(BigDecimal left, BigDecimal right) {
        int compared = left.compareTo(right);
        return compared > 0 ? "above" : compared < 0 ? "below" : "equal to";
    }

    private static String show(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
