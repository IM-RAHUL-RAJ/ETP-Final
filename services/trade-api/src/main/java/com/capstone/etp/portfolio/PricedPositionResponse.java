package com.capstone.etp.portfolio;

import java.math.BigDecimal;
import java.time.Instant;


public record PricedPositionResponse(
        Long accountId,
        String symbol,
        int quantity,
        BigDecimal averageCost,
        BigDecimal costBasis,
        BigDecimal lastPrice,
        BigDecimal marketValue,
        BigDecimal unrealisedPnl,
        BigDecimal unrealisedPnlPercent,
        String currency,
        Instant priceAsOf,
        boolean stale) {
}
