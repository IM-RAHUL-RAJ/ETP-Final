package com.capstone.etp.executor.client;

import com.capstone.etp.executor.exception.RateLimitException;
import com.capstone.etp.executor.exception.PriceUnavailableException;
import com.capstone.etp.executor.exception.SymbolRequestException;
import com.capstone.etp.executor.exception.TransientNetworkException;
import com.capstone.etp.executor.kafka.models.QuotePayload;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.lang.reflect.Method;
import org.springframework.http.HttpHeaders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

class FauxnanceClientHttpTest {

    @Test
    void getQuote_returnsParsedQuote_for200Response() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody("""
                            {
                              "data": {
                                "symbol": "AAPL",
                                "price": 100,
                                "bid": 99,
                                "ask": 101,
                                "currency": "USD",
                                "asOf": "2026-01-01T00:00:00Z"
                              },
                              "meta": {
                                "stale": false
                              }
                            }
                            """));
            server.start();

            FauxnanceClient client = new FauxnanceClient(
                    server.url("/").toString(),
                    "k",
                    1,
                    0,
                    new ObjectMapper().findAndRegisterModules()
            );

            QuotePayload quote = client.getQuote("AAPL");
            assertEquals("AAPL", quote.getSymbol());
            assertEquals("USD", quote.getCurrency());
            assertFalse(Boolean.TRUE.equals(quote.getStale()));
        }
    }

    @Test
    void getQuote_throwsPriceUnavailable_forBlankBody() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setResponseCode(200).setBody(" "));
            server.start();

            FauxnanceClient client = new FauxnanceClient(
                    server.url("/").toString(),
                    "k",
                    1,
                    0,
                    new ObjectMapper().findAndRegisterModules()
            );

            assertThrows(PriceUnavailableException.class, () -> client.getQuote("AAPL"));
        }
    }

    @Test
    void getQuote_retriesAndFails_whenEndpointUnavailable() {
        FauxnanceClient client = new FauxnanceClient(
                "http://127.0.0.1:1",
                "k",
                2,
                0,
                new ObjectMapper().findAndRegisterModules()
        );

        assertThrows(PriceUnavailableException.class, () -> client.getQuote("AAPL"));
    }

    @Test
    void getBatchQuotes_returnsEmpty_forNullOrEmptyInput() {
        FauxnanceClient client = new FauxnanceClient(
                "http://127.0.0.1:1",
                "k",
                1,
                0,
                new ObjectMapper().findAndRegisterModules()
        );

        assertTrue(client.getBatchQuotes(null).isEmpty());
        assertTrue(client.getBatchQuotes(List.of()).isEmpty());
    }

    @Test
    void getBatchQuotes_returnsParsedData_for200Response() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody("""
                            {
                              "data": {
                                "quotes": [
                                  {"symbol": "AAPL", "stale": false, "quote": {"price": 100, "bid": 99, "ask": 101, "currency": "USD"}}
                                ]
                              }
                            }
                            """));
            server.start();

            FauxnanceClient client = new FauxnanceClient(
                    server.url("/").toString(),
                    "k",
                    1,
                    0,
                    new ObjectMapper().findAndRegisterModules()
            );

            List<QuotePayload> quotes = client.getBatchQuotes(List.of("AAPL"));
            assertEquals(1, quotes.size());
            assertEquals("AAPL", quotes.get(0).getSymbol());
            assertEquals("USD", quotes.get(0).getCurrency());
        }
    }

    @Test
    void getBatchQuotes_returnsEmpty_forBlankBody() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setResponseCode(200).setBody("   "));
            server.start();

            FauxnanceClient client = new FauxnanceClient(
                    server.url("/").toString(),
                    "k",
                    1,
                    0,
                    new ObjectMapper().findAndRegisterModules()
            );

            assertTrue(client.getBatchQuotes(List.of("AAPL")).isEmpty());
        }
    }

    @Test
    void getBatchQuotes_retries202ThenSucceeds() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setResponseCode(202).setHeader("Retry-After", "0").setBody("{}"));
            server.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody("[{\"symbol\":\"MSFT\",\"price\":1,\"bid\":1,\"ask\":1,\"currency\":\"USD\"}]"));
            server.start();

            FauxnanceClient client = new FauxnanceClient(
                    server.url("/").toString(),
                    "k",
                    2,
                    0,
                    new ObjectMapper().findAndRegisterModules()
            );

            List<QuotePayload> quotes = client.getBatchQuotes(List.of("MSFT"));
            assertEquals(1, quotes.size());
            assertEquals("MSFT", quotes.get(0).getSymbol());
        }
    }

    @Test
    void getBatchQuotes_throwsRateLimitException_for429() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setResponseCode(429).setHeader("Retry-After", "3").setBody("limit"));
            server.start();

            FauxnanceClient client = new FauxnanceClient(
                    server.url("/").toString(),
                    "k",
                    1,
                    0,
                    new ObjectMapper().findAndRegisterModules()
            );

            RateLimitException ex = assertThrows(RateLimitException.class,
                    () -> client.getBatchQuotes(List.of("AAPL")));
            assertEquals(3, ex.getRetryAfterSeconds());
        }
    }

    @Test
    void getBatchQuotes_throwsSymbolRequestException_for400() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setResponseCode(400).setBody("bad symbols"));
            server.start();

            FauxnanceClient client = new FauxnanceClient(
                    server.url("/").toString(),
                    "k",
                    1,
                    0,
                    new ObjectMapper().findAndRegisterModules()
            );

            SymbolRequestException ex = assertThrows(SymbolRequestException.class,
                    () -> client.getBatchQuotes(List.of("AAPL", "MSFT")));
            assertTrue(ex.getMessage().contains("status=400"));
        }
    }

    @Test
    void getBatchQuotes_throwsTransientNetworkException_afterServerErrors() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setResponseCode(503).setBody("unavailable"));
            server.enqueue(new MockResponse().setResponseCode(500).setBody("still unavailable"));
            server.start();

            FauxnanceClient client = new FauxnanceClient(
                    server.url("/").toString(),
                    "k",
                    2,
                    0,
                    new ObjectMapper().findAndRegisterModules()
            );

            assertThrows(TransientNetworkException.class,
                    () -> client.getBatchQuotes(List.of("AAPL")));
        }
    }

    @Test
    void getBatchQuotes_wrapsRuntimeParseErrors_asTransientNetworkException() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setResponseCode(200).setBody("{not-valid-json"));
            server.start();

            FauxnanceClient client = new FauxnanceClient(
                    server.url("/").toString(),
                    "k",
                    1,
                    0,
                    new ObjectMapper().findAndRegisterModules()
            );

            assertThrows(TransientNetworkException.class,
                    () -> client.getBatchQuotes(List.of("AAPL")));
        }
    }

    @Test
    void helperMethods_computeRetryDelayAndParseRetryAfter() throws Exception {
        FauxnanceClient client = new FauxnanceClient(
                "http://127.0.0.1:1",
                "k",
                1,
                10,
                new ObjectMapper().findAndRegisterModules()
        );

        Method retryAfterMethod = FauxnanceClient.class.getDeclaredMethod("retryAfterSeconds", HttpHeaders.class);
        retryAfterMethod.setAccessible(true);
        Method retryDelayMethod = FauxnanceClient.class.getDeclaredMethod("retryDelayMs", HttpHeaders.class, int.class);
        retryDelayMethod.setAccessible(true);

        HttpHeaders headers = new HttpHeaders();
        headers.add("Retry-After", "5");
        Integer retryAfter = (Integer) retryAfterMethod.invoke(client, headers);
        long retryDelay = (long) retryDelayMethod.invoke(client, headers, 1);

        HttpHeaders invalidHeaders = new HttpHeaders();
        invalidHeaders.add("Retry-After", "abc");
        Integer invalidRetryAfter = (Integer) retryAfterMethod.invoke(client, invalidHeaders);
        long fallbackDelay = (long) retryDelayMethod.invoke(client, invalidHeaders, 2);

        assertEquals(5, retryAfter);
        assertEquals(5000L, retryDelay);
        assertEquals(null, invalidRetryAfter);
        assertEquals(20L, fallbackDelay);
    }
}
