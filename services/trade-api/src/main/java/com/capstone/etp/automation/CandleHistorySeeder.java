package com.capstone.etp.automation;

import com.capstone.etp.candles.CandleService;
import com.capstone.etp.candles.CandlesResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

/** Starting prices from the 15-minute candles (the same provider the watchlist chart reads). Never throws. */
@Slf4j
@Component
class CandleHistorySeeder implements HistorySeeder {

    private static final int MAX_LIMIT = 500;

    private final CandleService candles;

    CandleHistorySeeder(CandleService candles) {
        this.candles = candles;
    }

    @Override
    public List<BigDecimal> recentCloses(String symbol, int count) {
        try {
            CandlesResponse response = candles.candles(symbol, "15m", Math.min(Math.max(count, 1), MAX_LIMIT));
            if (response == null || response.candles() == null) {
                return List.of();
            }
            return response.candles().stream()
                    .filter(candle -> candle.close() != null && candle.timestamp() != null && candle.close().signum() > 0)
                    .sorted(Comparator.comparing(CandlesResponse.Candle::timestamp))
                    .map(CandlesResponse.Candle::close)
                    .toList();
        } catch (RuntimeException e) {
            log.warn("[AUTOMATION] no starting prices for {}: {}", symbol, e.getClass().getSimpleName());
            return List.of();
        }
    }
}
