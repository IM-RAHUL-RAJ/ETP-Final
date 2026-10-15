package com.capstone.etp.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * The latest quote the executor published to the market-data topic for one symbol.
 * It is both the Kafka payload shape and what the watchlist endpoint returns.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class MarketQuote {
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
    /** When this service received it from Kafka. Not part of the topic payload. */
    private Instant receivedAt;
}
