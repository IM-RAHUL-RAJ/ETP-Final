package com.capstone.etp.executor.kafka.models;

import lombok.AllArgsConstructor;
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
public class TradeEventPayload {

    private UUID orderId;

    private Long accountId;

    private String symbol;

    private String side;

    private Integer quantity;

    private BigDecimal price;

    private BigDecimal executedPrice;

    private String status;

    private String reason;

    private BigDecimal cashDelta;

    private Integer positionQuantityAfter;

    private BigDecimal averageCostAfter;

    private Instant executedOn;
}