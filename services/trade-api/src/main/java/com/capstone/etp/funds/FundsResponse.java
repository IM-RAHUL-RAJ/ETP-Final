package com.capstone.etp.funds;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** One cash movement and the balance it left behind. */
public record FundsResponse(
        UUID id,
        Long accountId,
        FundsKind kind,
        BigDecimal amount,
        String currency,
        FundsMethod method,
        BigDecimal balanceAfter,
        Instant createdAt) {
}
