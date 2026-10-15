package com.capstone.etp.executor.client;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

//Fauxnance quote response.
@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class FauxnanceQuoteResponse {
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

    @JsonAlias({"asOf", "quoteAsOf"})
    private Instant quoteAsOf;
}
