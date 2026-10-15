package com.capstone.etp.entity;

import com.capstone.etp.enums.OrderSide;
import com.capstone.etp.enums.OrderStatus;
import com.capstone.etp.exception.IllegalOrderStateException;

import jakarta.validation.constraints.Positive;
import lombok.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Order {
    private UUID id;
    private Long accountId;
    private Long instrumentId;
    private UUID idempotencyKey;
    private OrderSide side;
    private int quantity;
    private BigDecimal limitPrice;
    private OrderStatus status;
    private Instant createdAt;
    private Instant terminalAt;

    private boolean isTerminal() {
        return status == OrderStatus.FILLED
                || status == OrderStatus.REJECTED
                || status == OrderStatus.CANCELLED;
    }

    public void fill() {
        ensureNotTerminal();
        status = OrderStatus.FILLED;
        terminalAt = Instant.now();
    }

    public void reject() {
        ensureNotTerminal();
        status = OrderStatus.REJECTED;
        terminalAt = Instant.now();
    }

    public void cancel() {
        ensureNotTerminal();
        status = OrderStatus.CANCELLED;
        terminalAt = Instant.now();
    }

    private void ensureNotTerminal() {
        if (isTerminal()) {
            throw new IllegalOrderStateException(
                    "Cannot change an order that has already reached a terminal state");
        }
    }
}