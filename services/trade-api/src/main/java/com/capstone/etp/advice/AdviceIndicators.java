package com.capstone.etp.advice;

import java.math.BigDecimal;

/**
 * What the rules looked at, for the customer to see. A field is null when there is not enough history for it.
 * rsiState is OVERBOUGHT (70 or more), OVERSOLD (30 or less) or NORMAL.
 */
public record AdviceIndicators(
        BigDecimal lastPrice,
        BigDecimal shortAverage,
        BigDecimal longAverage,
        int shortPeriod,
        int longPeriod,
        BigDecimal rsi,
        String rsiState,
        BigDecimal momentumPercent,
        BigDecimal volatilityPercent,
        MarketTrend trend) {
}
