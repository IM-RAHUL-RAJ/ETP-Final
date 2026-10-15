package com.capstone.etp.kafka.models;

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
public class OrderPlacedPayload {

    private UUID orderId;

    private Long accountId;

    private String symbol;

    private String side;

    private Integer quantity;

    private BigDecimal price;

    private String idempotencyKey;

    private Instant createdOn;
}