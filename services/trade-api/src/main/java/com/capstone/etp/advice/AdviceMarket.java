package com.capstone.etp.advice;

import java.util.List;

/** The indicators and the chart series that sit beside a signal. */
public record AdviceMarket(AdviceIndicators indicators, List<ChartPoint> chart) {

    public static final AdviceMarket NONE = new AdviceMarket(null, List.of());
}
