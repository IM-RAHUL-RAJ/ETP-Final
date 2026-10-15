package com.capstone.etp.automation;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** One trigger of a strategy and what became of its order, as kept in automation_events. */
@Getter
@Setter
public class AutomationEvent {
    private Long id;
    private Long strategyId;
    private Long accountId;
    private String strategyType;
    private String symbol;
    private String triggerSide;
    private String outcome;
    private UUID idempotencyKey;
    private UUID orderId;
    private BigDecimal limitPrice;
    private Integer quantity;
    private String detail;
    private Instant createdAt;
    private Instant updatedAt;
}
