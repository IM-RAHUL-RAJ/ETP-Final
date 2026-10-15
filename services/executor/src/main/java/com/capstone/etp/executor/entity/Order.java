package com.capstone.etp.executor.entity;

import com.capstone.etp.executor.enums.OrderSide;
import com.capstone.etp.executor.enums.OrderStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

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
}

