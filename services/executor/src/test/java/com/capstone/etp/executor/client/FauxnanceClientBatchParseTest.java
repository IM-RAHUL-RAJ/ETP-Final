package com.capstone.etp.executor.client;

import com.capstone.etp.executor.kafka.models.QuotePayload;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FauxnanceClientBatchParseTest {

    @SuppressWarnings("unchecked")
    @Test
    void parsesWrappedQuotesArrayAndSkipsErrorEntries() throws Exception {
        FauxnanceClient client = new FauxnanceClient(
                "http://localhost",
                "dummy-key",
                3,
                300,
                new ObjectMapper().findAndRegisterModules());

        String body = """
                {
                  "data": {
                    "quotes": [
                      {
                        "symbol": "AAPL",
                        "source": "cache",
                        "stale": false,
                        "quote": {
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
                        }
                      },
                      {
                        "symbol": "MSFT",
                        "source": "cache",
                        "stale": false,
                        "quote": {
                          "symbol": "MSFT",
                          "price": 490.3,
                          "bid": 490.23,
                          "ask": 490.37,
                          "currency": "USD",
                          "change": -6.82,
                          "changePercent": -1.3719021564209848,
                          "previousClose": 497.12,
                          "asOf": "2026-09-16T20:00:01Z",
                          "marketState": "unknown"
                        }
                      },
                      {
                        "symbol": "GOOGL",
                        "source": "upstream:yahoo",
                        "stale": false,
                        "quote": {
                          "symbol": "GOOGL",
                          "price": 342.87,
                          "bid": 342.82,
                          "ask": 342.92,
                          "currency": "USD",
                          "change": -2.11,
                          "changePercent": -0.6116296596904168,
                          "previousClose": 344.98,
                          "asOf": "2026-09-16T20:00:01Z",
                          "marketState": "unknown"
                        }
                      },
                      {
                        "symbol": "TSLA",
                        "source": "upstream:yahoo",
                        "stale": false,
                        "quote": {
                          "symbol": "TSLA",
                          "price": 358.08,
                          "bid": 358.01,
                          "ask": 358.15,
                          "currency": "USD",
                          "change": 1.5,
                          "changePercent": 0.42066296483257615,
                          "previousClose": 356.58,
                          "asOf": "2026-09-16T20:00:01Z",
                          "marketState": "unknown"
                        }
                      },
                      {
                        "symbol": "WCOM",
                        "error": {
                          "code": "BACKFILL_IN_PROGRESS",
                          "message": "Quote data is being prepared; retry later.",
                          "details": {}
                        }
                      },
                      {
                        "symbol": "RELIANCE.NS",
                        "source": "cache",
                        "stale": false,
                        "quote": {
                          "symbol": "RELIANCE.NS",
                          "price": 1245.7,
                          "bid": 1245.51,
                          "ask": 1245.89,
                          "currency": "INR",
                          "change": 5.7,
                          "changePercent": 0.4596774193548387,
                          "previousClose": 1240,
                          "asOf": "2026-09-17T07:21:48Z",
                          "marketState": "unknown"
                        }
                      }
                    ]
                  }
                }
                """;

        Method parse = FauxnanceClient.class.getDeclaredMethod("parseBatchQuoteResponse", String.class);
        parse.setAccessible(true);

        List<QuotePayload> parsed = (List<QuotePayload>) parse.invoke(client, body);

        assertEquals(5, parsed.size());
        Set<String> symbols = parsed.stream().map(QuotePayload::getSymbol).collect(Collectors.toSet());
        assertTrue(symbols.contains("AAPL"));
        assertTrue(symbols.contains("MSFT"));
        assertTrue(symbols.contains("GOOGL"));
        assertTrue(symbols.contains("TSLA"));
        assertTrue(symbols.contains("RELIANCE.NS"));
        assertFalse(symbols.contains("WCOM"));
    }
}
