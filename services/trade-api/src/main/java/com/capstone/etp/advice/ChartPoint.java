package com.capstone.etp.advice;

import java.math.BigDecimal;
import java.time.Instant;

/** One point of the advice chart: the close and the two moving averages at that point (null before there is enough history). */
public record ChartPoint(Instant at, BigDecimal close, BigDecimal shortAverage, BigDecimal longAverage) {
}
