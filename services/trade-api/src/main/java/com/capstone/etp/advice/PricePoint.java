package com.capstone.etp.advice;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** One closing price, with the time it belongs to when the provider knows it. */
public record PricePoint(Instant at, BigDecimal close) {

    /** Closes with no times (the chart then labels them by position). */
    public static List<PricePoint> undated(List<BigDecimal> closes) {
        return closes == null ? List.of() : closes.stream().map(close -> new PricePoint(null, close)).toList();
    }
}
