package com.capstone.etp.candles;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record CandlesResponse(String symbol, String currency, String period, List<Candle> candles, Instant asOf) {

    public record Candle(Instant timestamp, BigDecimal open, BigDecimal high, BigDecimal low, BigDecimal close, Long volume) {
    }
}
