package com.capstone.etp.advice;

import java.time.Instant;
import java.util.List;

/**
 * One piece of advice for one instrument. The reasoning lists the figures the rules actually used; indicators and chart
 * show the market behind the signal (both may be missing when there is no price history at all).
 */
public record TradeAdvice(String symbol, AdviceSignal signal, List<String> reasoning, Instant timestamp,
                          AdviceIndicators indicators, List<ChartPoint> chart) {

    public TradeAdvice(String symbol, AdviceSignal signal, List<String> reasoning, Instant timestamp) {
        this(symbol, signal, reasoning, timestamp, null, List.of());
    }

    public TradeAdvice withMarket(AdviceMarket market) {
        return new TradeAdvice(symbol, signal, reasoning, timestamp, market.indicators(), market.chart());
    }
}
