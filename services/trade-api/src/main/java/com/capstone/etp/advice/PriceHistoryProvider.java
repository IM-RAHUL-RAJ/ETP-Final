package com.capstone.etp.advice;

import java.math.BigDecimal;
import java.util.List;

/** Where the advice gets closing prices from. Behind an interface so the rules can be tested without a market. */
public interface PriceHistoryProvider {

    /**
     * Up to {@code count} of the latest daily closing prices, oldest first. Empty when the market data cannot be had:
     * the caller treats that as "insufficient data", never as a reason to guess.
     */
    List<BigDecimal> closes(String symbol, int count);

    /** The same closes with their times, when the provider has them. By default the closes alone. */
    default List<PricePoint> points(String symbol, int count) {
        return PricePoint.undated(closes(symbol, count));
    }
}
