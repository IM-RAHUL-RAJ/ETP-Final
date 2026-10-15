package com.capstone.etp.candles;

import com.capstone.etp.exception.ModuleException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CandleServiceTest {

    // The base URL is never called in these tests: validation and parsing happen before and after the request.
    private final CandleService service = new CandleService("http://localhost:1", "", 100, 100, 60, new ObjectMapper());

    @Test
    void theSymbolPeriodAndLimitAreValidatedBeforeAnyCall() {
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY,
                assertThrows(ModuleException.class, () -> service.candles("../etc", "1d", 30)).getStatus());
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY,
                assertThrows(ModuleException.class, () -> service.candles("AAPL", "5m", 30)).getStatus());
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY,
                assertThrows(ModuleException.class, () -> service.candles("AAPL", "1d", 0)).getStatus());
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY,
                assertThrows(ModuleException.class, () -> service.candles("AAPL", "1d", CandleService.MAX_LIMIT + 1)).getStatus());
    }

    @Test
    void anUnreachableProviderIs503NotAnInventedChart() {
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE,
                assertThrows(ModuleException.class, () -> service.candles("AAPL", "1d", 30)).getStatus());
    }

    @Test
    void candlesAreReadInOldestFirstOrderAndTrimmedToTheLimit() {
        String body = """
                {"data":{"currency":"USD","candles":[
                  {"timestamp":"2026-10-03T00:00:00Z","open":3,"high":4,"low":2,"close":3.5,"volume":300},
                  {"timestamp":"2026-10-01T00:00:00Z","open":1,"high":2,"low":0.5,"close":1.5,"volume":100},
                  {"timestamp":"2026-10-02T00:00:00Z","open":2,"high":3,"low":1,"close":2.5,"volume":200}]}}""";

        CandlesResponse response = service.parse("AAPL", CandlePeriod.ONE_DAY, 2, body);

        assertEquals("USD", response.currency());
        assertEquals(2, response.candles().size());
        assertEquals(new BigDecimal("2.5"), response.candles().get(0).close(), "oldest of the last two");
        assertEquals(new BigDecimal("3.5"), response.candles().get(1).close());
    }

    @Test
    void aShortKeyAnswerAndEpochSecondsAreUnderstood() {
        CandlesResponse response = service.parse("AAPL", CandlePeriod.FIFTEEN_MIN, 10,
                "[{\"t\":1790000000,\"o\":1,\"h\":2,\"l\":0.5,\"c\":1.5,\"v\":10}]");

        assertEquals(1, response.candles().size());
        assertEquals(new BigDecimal("1.5"), response.candles().get(0).close());
        assertEquals(10L, response.candles().get(0).volume());
    }

    @Test
    void aBrokenCandleIsDroppedRatherThanDrawn() {
        CandlesResponse response = service.parse("AAPL", CandlePeriod.ONE_DAY, 10,
                "[{\"timestamp\":\"2026-10-01T00:00:00Z\",\"open\":1,\"high\":2,\"low\":0.5},{\"timestamp\":\"2026-10-02T00:00:00Z\",\"open\":1,\"high\":2,\"low\":0.5,\"close\":1.2}]");

        assertEquals(1, response.candles().size());
        assertNull(response.candles().get(0).volume());
    }
}
