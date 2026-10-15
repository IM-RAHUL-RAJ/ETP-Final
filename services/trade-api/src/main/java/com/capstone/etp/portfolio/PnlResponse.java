package com.capstone.etp.portfolio;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record PnlResponse(
        Long accountId,
        String baseCurrency,
        LocalDate from,
        LocalDate to,
        BigDecimal realisedPnl,
        BigDecimal unrealisedPnl,
        BigDecimal totalPnl,
        @JsonInclude(JsonInclude.Include.NON_NULL) List<SymbolPnl> bySymbol,
        Instant asOf) {

    /** contracts/portfolio-api.yaml SymbolPnl. */
    public record SymbolPnl(String symbol, BigDecimal realisedPnl, BigDecimal unrealisedPnl, BigDecimal totalPnl) {
    }
}
