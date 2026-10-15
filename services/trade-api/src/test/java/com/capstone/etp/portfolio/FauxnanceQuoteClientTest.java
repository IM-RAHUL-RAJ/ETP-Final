package com.capstone.etp.portfolio;

import com.capstone.etp.config.JacksonConfig;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FauxnanceQuoteClientTest {

    private HttpServer server;
    private final List<String> requestLines = new CopyOnWriteArrayList<>();
    private final List<String> apiKeys = new CopyOnWriteArrayList<>();
    private final AtomicInteger status = new AtomicInteger(200);
    private volatile String body = "{}";

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            requestLines.add(exchange.getRequestURI().toString());
            apiKeys.add(String.valueOf(exchange.getRequestHeaders().getFirst("X-API-Key")));
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status.get(), bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private FauxnanceQuoteClient client(String key) {
        return new FauxnanceQuoteClient("http://127.0.0.1:" + server.getAddress().getPort(), key, 1000, 2000,
                new JacksonConfig().objectMapper());
    }

    private static final String WRAPPED = """
            {"data":{"quotes":[
              {"symbol":"AAPL","source":"cache","stale":false,
               "quote":{"symbol":"AAPL","price":332.41,"bid":332.37,"ask":332.45,"currency":"USD","asOf":"2026-09-16T20:00:01Z"}},
              {"symbol":"MSFT","source":"cache","stale":true,
               "quote":{"symbol":"MSFT","price":490.3,"currency":"USD","asOf":"2026-09-16T19:00:00Z"}},
              {"symbol":"NOPE","error":{"code":"NOT_FOUND"}}
            ]}}""";

    @Test
    void oneCallCarriesEverySymbolAndTheKeyGoesInAHeaderNeverTheUrl() throws Exception {
        body = WRAPPED;

        Map<String, QuoteProvider.ProviderQuote> quotes = client("secret-key-123").fetch(List.of("AAPL", "MSFT", "NOPE"));

        assertEquals(1, requestLines.size(), "one request for three symbols");
        assertTrue(requestLines.get(0).startsWith("/quotes?symbols=AAPL"), requestLines.get(0));
        assertTrue(requestLines.get(0).contains("MSFT"));
        assertEquals("secret-key-123", apiKeys.get(0));
        assertFalse(requestLines.get(0).contains("secret-key-123"), "the key must not be in the URL");
        assertEquals(2, quotes.size(), "the symbol with an error entry is left out, not guessed");
    }

    @Test
    void thePriceTheObservationTimeAndTheProvidersStaleFlagAreKept() throws Exception {
        body = WRAPPED;

        Map<String, QuoteProvider.ProviderQuote> quotes = client("k").fetch(List.of("AAPL", "MSFT"));

        QuoteProvider.ProviderQuote aapl = quotes.get("AAPL");
        assertEquals(new BigDecimal("332.41"), aapl.price());
        assertEquals("USD", aapl.currency());
        assertEquals(Instant.parse("2026-09-16T20:00:01Z"), aapl.asOf());
        assertFalse(aapl.stale());
        assertTrue(quotes.get("MSFT").stale());
    }

    @Test
    void aBareArrayAnswerIsAlsoUnderstood() throws Exception {
        body = "[{\"symbol\":\"AAPL\",\"price\":1.5,\"currency\":\"USD\",\"asOf\":\"2026-09-16T20:00:01Z\"}]";

        assertEquals(new BigDecimal("1.5"), client("k").fetch(List.of("AAPL")).get("AAPL").price());
    }

    @Test
    void aServerErrorIsAProviderFailureWithoutLeakingTheKeyOrUrl() {
        status.set(500);
        body = "{\"error\":\"boom\"}";

        QuoteProvider.QuoteProviderException failure = assertThrows(QuoteProvider.QuoteProviderException.class,
                () -> client("secret-key-123").fetch(List.of("AAPL")));

        assertFalse(failure.getMessage().contains("secret-key-123"));
        assertFalse(failure.getMessage().contains("127.0.0.1"));
    }

    @Test
    void quotaExhaustedIsAProviderFailure() {
        status.set(429);

        assertThrows(QuoteProvider.QuoteProviderException.class, () -> client("k").fetch(List.of("AAPL")));
    }

    @Test
    void anUnreachableProviderIsAProviderFailure() {
        server.stop(0);

        assertThrows(QuoteProvider.QuoteProviderException.class, () -> client("k").fetch(List.of("AAPL")));
    }

    @Test
    void moreThanTwentyFiveSymbolsInOneCallIsRefusedLocally() {
        List<String> tooMany = new ArrayList<>();
        for (int i = 0; i < 26; i++) {
            tooMany.add("S" + i);
        }

        assertThrows(IllegalArgumentException.class, () -> client("k").fetch(tooMany));
        assertTrue(requestLines.isEmpty());
    }

    @Test
    void nonPositiveOrMissingPricesAreNotQuotes() throws Exception {
        body = "{\"data\":{\"quotes\":[{\"symbol\":\"A\",\"quote\":{\"symbol\":\"A\",\"price\":0}},"
                + "{\"symbol\":\"B\",\"quote\":{\"symbol\":\"B\"}}]}}";

        assertTrue(client("k").fetch(List.of("A", "B")).isEmpty());
    }

    @Test
    void usageIsReadFromTheUsageEndpoint() throws Exception {
        body = "{\"data\":{\"quotaRemaining\":1873}}";

        OptionalInt remaining = client("k").usageRemaining();

        assertEquals(1873, remaining.getAsInt());
        assertEquals("/usage", requestLines.get(0));
    }

    @Test
    void anUnreadableUsageAnswerIsNotAnError() throws Exception {
        body = "{\"something\":\"else\"}";

        assertFalse(client("k").usageRemaining().isPresent());
    }
}
