package com.capstone.etp.alerts;

import java.math.BigDecimal;
import java.time.Instant;

/** One alert as the list shows it: "symbol condition price", on or off, and how often it has fired. */
public record AlertResponse(
        Long id,
        String symbol,
        AlertOperator condition,
        BigDecimal price,
        boolean enabled,
        AlertStatus status,
        DeliveryState deliveryState,
        BigDecimal triggeredPrice,
        Instant triggeredAt,
        Instant createdAt,
        long triggerCount) {

    static AlertResponse from(PriceAlert alert) {
        return new AlertResponse(
                alert.getId(),
                alert.getSymbol(),
                alert.getOperator(),
                alert.getThreshold(),
                alert.isEnabled(),
                alert.getStatus(),
                alert.getDeliveryState(),
                alert.getTriggeredPrice(),
                alert.getTriggeredAt(),
                alert.getCreatedAt(),
                alert.getTriggerCount());
    }
}
