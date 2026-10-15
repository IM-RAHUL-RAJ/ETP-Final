package com.capstone.etp.automation;

import java.time.Instant;
import java.util.UUID;

/** A record of one trigger and what became of its order. */
public record AutomationResult(String strategyId, String symbol, Long accountId, SignalType trigger,
                               OrderOutcome outcome, UUID orderId, String detail, Instant at) {
}
