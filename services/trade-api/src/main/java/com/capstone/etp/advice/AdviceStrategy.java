package com.capstone.etp.advice;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * The rules that turn price history into a signal. Kept apart from everything that fetches data, so the
 * rules can be changed or replaced without touching the service or the API.
 */
public interface AdviceStrategy {

    /** How many closing prices the rules need before they will say anything. */
    int requiredObservations();

    /**
     * @param closes closing prices, oldest first (may be shorter than required, or empty)
     */
    TradeAdvice evaluate(String symbol, List<BigDecimal> closes, Instant now);

    /** The indicators and the chart series for the same history, to show beside the signal. */
    AdviceMarket market(List<PricePoint> history);
}
