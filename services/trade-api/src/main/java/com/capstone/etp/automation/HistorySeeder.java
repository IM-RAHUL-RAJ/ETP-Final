package com.capstone.etp.automation;

import java.math.BigDecimal;
import java.util.List;

/**
 * Where a strategy gets its starting prices from, so it does not have to sit and wait for a full window of live quotes
 * before it can act. The live stream takes over from the next tick.
 */
public interface HistorySeeder {

    /** Up to {@code count} recent closing prices, oldest first. Empty when they cannot be had (the strategy then warms up live). */
    List<BigDecimal> recentCloses(String symbol, int count);
}
