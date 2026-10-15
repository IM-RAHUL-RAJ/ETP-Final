package com.capstone.etp.executor.service;

import com.capstone.etp.executor.enums.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;


public record SettlementResult(
        UUID orderId,
        Long accountId,
        String symbol,
        String side,
        int quantity,
        BigDecimal limitPrice,
        BigDecimal executedPrice,       // null for rejected orders
        OrderStatus status,
        String reason,                  // null for filled orders
        BigDecimal cashDelta,           // negative = BUY, positive = SELL, 0 = reject
        int positionQuantityAfter,
        BigDecimal averageCostAfter,
        Instant executedOn,
        boolean alreadyHandled
) {
   
    public static SettlementResult alreadyHandled(UUID orderId) {
        return new SettlementResult(
                orderId, null, null, null, 0,
                null, null, null, null,
                BigDecimal.ZERO, 0, BigDecimal.ZERO,
                null, true);
    }
}

