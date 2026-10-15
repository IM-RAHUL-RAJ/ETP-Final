package com.capstone.etp.executor.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class FauxnanceClientSingleQuoteParseTest {

    @Test
    void parsesWrappedSingleQuoteAndAppliesMetaStale() throws Exception {
        FauxnanceClient client = new FauxnanceClient(
                "http://localhost",
                "dummy-key",
                3,
                300,
                new ObjectMapper().findAndRegisterModules());

        String wrapped = """
                {
                  "data": {
                    "symbol": "AAPL",
                    "price": 332.41,
                    "bid": 332.37,
                    "ask": 332.45,
                    "currency": "USD",
                    "change": 1.07,
                    "changePercent": 0.32293112814631497,
                    "previousClose": 331.34,
                    "asOf": "2026-09-16T20:00:01Z",
                    "marketState": "unknown"
                  },
                  "meta": {
                    "stale": false
                  }
                }
                """;

        Method parseSingleQuote = FauxnanceClient.class.getDeclaredMethod("parseSingleQuote", String.class);
        parseSingleQuote.setAccessible(true);

        FauxnanceQuoteResponse response = (FauxnanceQuoteResponse) parseSingleQuote.invoke(client, wrapped);

        assertNotNull(response);
        assertEquals("AAPL", response.getSymbol());
        assertEquals(new BigDecimal("332.41"), response.getPrice());
        assertEquals(new BigDecimal("332.37"), response.getBid());
        assertEquals(new BigDecimal("332.45"), response.getAsk());
        assertEquals("USD", response.getCurrency());
        assertEquals(Instant.parse("2026-09-16T20:00:01Z"), response.getQuoteAsOf());
        assertEquals("unknown", response.getMarketState());
        assertNotNull(response.getStale());
        assertFalse(response.getStale());
    }
}
