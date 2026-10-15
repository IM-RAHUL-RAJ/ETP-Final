package com.capstone.etp.portfolio;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** The portfolio's value over time, and what the last day did. dayChange is null until there is a point a day old. */
public record PortfolioHistoryResponse(
        Long accountId,
        String baseCurrency,
        String range,
        List<Point> points,
        DayChange dayChange) {

    public record Point(Instant at, BigDecimal totalValue) {
    }

    /** The change since yesterday close, or since the latest recorded point a day old (then with cash added or taken out removed). since is null for the first. */
    public record DayChange(BigDecimal amount, BigDecimal percent, Instant since) {
    }
}
