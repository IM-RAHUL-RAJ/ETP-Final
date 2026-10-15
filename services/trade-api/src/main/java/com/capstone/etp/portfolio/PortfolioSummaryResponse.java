package com.capstone.etp.portfolio;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.math.BigDecimal;
import java.time.Instant;


/** The summary the contract describes. dayChange stays inside the platform (it feeds the history endpoint), so the JSON keeps its twelve fields. */
public record PortfolioSummaryResponse(
        Long accountId,
        String baseCurrency,
        BigDecimal cashBalance,
        BigDecimal marketValue,
        BigDecimal costBasis,
        BigDecimal unrealisedPnl,
        BigDecimal unrealisedPnlPercent,
        BigDecimal realisedPnl,
        BigDecimal totalValue,
        int positionCount,
        boolean partial,
        Instant asOf,
        @JsonIgnore BigDecimal dayChange,
        @JsonIgnore BigDecimal dayChangePercent) {

    /** Without a "today": the figure is null when no holding had a previous close to measure from. */
    public PortfolioSummaryResponse(Long accountId, String baseCurrency, BigDecimal cashBalance, BigDecimal marketValue,
                                    BigDecimal costBasis, BigDecimal unrealisedPnl, BigDecimal unrealisedPnlPercent,
                                    BigDecimal realisedPnl, BigDecimal totalValue, int positionCount, boolean partial,
                                    Instant asOf) {
        this(accountId, baseCurrency, cashBalance, marketValue, costBasis, unrealisedPnl, unrealisedPnlPercent,
                realisedPnl, totalValue, positionCount, partial, asOf, null, null);
    }
}
