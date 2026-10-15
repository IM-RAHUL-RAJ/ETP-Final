package com.capstone.etp.funds;

import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** One row of cash_movements. */
@Data
public class FundsMovement {
    private UUID id;
    private Long accountId;
    private FundsKind kind;
    private BigDecimal amount;
    private String currency;
    private FundsMethod method;
    private BigDecimal balanceAfter;
    private Instant createdAt;
}
