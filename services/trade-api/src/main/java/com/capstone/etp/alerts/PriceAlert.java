package com.capstone.etp.alerts;

import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

//One row of price_alerts.
@Data
public class PriceAlert {
    private Long id;
    private Long accountId;
    private String symbol;
    private AlertDirection direction;
    private AlertOperator operator;
    private BigDecimal threshold;
    private boolean enabled;
    private AlertStatus status;
    private DeliveryState deliveryState;
    private int deliveryAttempts;
    private BigDecimal triggeredPrice;
    private Instant triggeredAt;
    private Instant createdAt;
    private long triggerCount;
}
