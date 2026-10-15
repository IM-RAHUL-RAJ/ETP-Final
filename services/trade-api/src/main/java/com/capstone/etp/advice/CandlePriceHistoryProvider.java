package com.capstone.etp.advice;

import com.capstone.etp.candles.CandleService;
import com.capstone.etp.candles.CandlesResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

/** Daily closes from the existing candle service (the same provider the watchlist chart reads). */
@Slf4j
@Component
class CandlePriceHistoryProvider implements PriceHistoryProvider {

    private static final int MAX_LIMIT = 500;

    private final CandleService candles;

    CandlePriceHistoryProvider(CandleService candles) {
        this.candles = candles;
    }

    @Override
    public List<BigDecimal> closes(String symbol, int count) {
        return points(symbol, count).stream().map(PricePoint::close).toList();
    }

    @Override
    public List<PricePoint> points(String symbol, int count) {
        try {
            CandlesResponse response = candles.candles(symbol, "1d", Math.min(Math.max(count, 1), MAX_LIMIT));
            if (response == null || response.candles() == null) {
                return List.of();
            }
            return response.candles().stream()
                    .filter(candle -> candle.close() != null && candle.timestamp() != null)
                    .sorted(Comparator.comparing(CandlesResponse.Candle::timestamp))
                    .map(candle -> new PricePoint(candle.timestamp(), candle.close()))
                    .toList();
        } catch (RuntimeException e) {
            // The provider being down is "no data", not a failure of the whole advice request.
            log.warn("[ADVICE] no price history for {}: {}", symbol, e.getClass().getSimpleName());
            return List.of();
        }
    }
}
