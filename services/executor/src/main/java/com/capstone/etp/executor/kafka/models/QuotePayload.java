package com.capstone.etp.executor.kafka.models;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class QuotePayload {

    private String symbol;

    private BigDecimal price;

    private BigDecimal bid;

    private BigDecimal ask;

    private String currency;

    private BigDecimal change;

    private BigDecimal changePercent;

    private BigDecimal previousClose;

    private String marketState;

    private Boolean stale;

    private Instant quoteAsOf;
}
