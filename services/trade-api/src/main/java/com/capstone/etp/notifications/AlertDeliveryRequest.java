package com.capstone.etp.notifications;

import java.math.BigDecimal;
import java.time.Instant;


/**
 * One firing of one alert, handed to notifications. {@code triggerId} identifies the firing, so an alert
 * that fires again later is a new message, while a retry of the same firing is not.
 */
public record AlertDeliveryRequest(
        long accountId,
        long alertId,
        long triggerId,
        String symbol,
        Operator operator,
        BigDecimal threshold,
        BigDecimal price,
        Instant observedAt) {

    /** The comparison the price satisfied. Owned here so notifications needs nothing from alerts. */
    public enum Operator {
        GT,
        GTE,
        LT,
        LTE,
        EQ
    }
}
