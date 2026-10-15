package com.capstone.etp.candles;

import com.capstone.etp.exception.ModuleException;
import com.capstone.etp.kafka.EventJson;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Candles for the watchlist chart, read from Fauxnance through the server so the API key never reaches the browser.
 * Answers are kept for a short while: a chart does not need a new request on every click, and the provider's quota is small.
 */
@Slf4j
@Service
public class CandleService {

    static final int MAX_LIMIT = 500;
    private static final Pattern SYMBOL = Pattern.compile("^[A-Za-z0-9.:^=\\-]{1,20}$");

    private record Cached(CandlesResponse response, long storedAtMillis) {
    }

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final long ttlMillis;
    private final Map<String, Cached> cache = new ConcurrentHashMap<>();

    CandleService(@Value("${app.fauxnance.base-url}") String baseUrl,
                  @Value("${app.fauxnance.api-key:}") String apiKey,
                  @Value("${app.fauxnance.connect-timeout-ms:2000}") int connectTimeoutMs,
                  @Value("${app.fauxnance.read-timeout-ms:4000}") int readTimeoutMs,
                  @Value("${app.candles.cache-seconds:60}") int cacheSeconds,
                  ObjectMapper objectMapper) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(connectTimeoutMs));
        factory.setReadTimeout(Duration.ofMillis(readTimeoutMs));
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .defaultHeader("X-API-Key", apiKey == null ? "" : apiKey)
                .build();
        this.objectMapper = objectMapper;
        this.ttlMillis = cacheSeconds * 1000L;
    }

    public CandlesResponse candles(String rawSymbol, String rawPeriod, Integer rawLimit) {
        if (rawSymbol == null || !SYMBOL.matcher(rawSymbol).matches()) {
            throw invalid("That is not a valid symbol");
        }
        CandlePeriod period = CandlePeriod.parse(rawPeriod);
        if (period == null) {
            throw invalid("period must be 1d, 4h, 1h or 15m");
        }
        int limit = rawLimit == null ? 30 : rawLimit;
        if (limit < 1 || limit > MAX_LIMIT) {
            throw invalid("limit must be between 1 and " + MAX_LIMIT);
        }
        String symbol = rawSymbol.trim().toUpperCase(Locale.ROOT);

        String key = symbol + "|" + period.code() + "|" + limit;
        Cached hit = cache.get(key);
        if (hit != null && System.currentTimeMillis() - hit.storedAtMillis() < ttlMillis) {
            return hit.response();
        }

        CandlesResponse fresh = fetch(symbol, period, limit);
        cache.put(key, new Cached(fresh, System.currentTimeMillis()));
        return fresh;
    }

    private CandlesResponse fetch(String symbol, CandlePeriod period, int limit) {
        String body;
        try {
            body = restClient.get()
                    .uri("/candles/{symbol}?period={period}&limit={limit}", symbol, period.code(), limit)
                    .retrieve()
                    .body(String.class);
        } catch (RestClientException e) {
            // The message can carry the URL; log the type only.
            log.warn("[CANDLES] provider call failed for {}: {}", symbol, e.getClass().getSimpleName());
            throw new ModuleException(HttpStatus.SERVICE_UNAVAILABLE, "MKT-503",
                    "Chart data is not available right now. Please try again shortly");
        }
        return parse(symbol, period, limit, body);
    }

    CandlesResponse parse(String symbol, CandlePeriod period, int limit, String body) {
        try {
            JsonNode root = EventJson.parse(objectMapper, body == null ? "[]" : body);
            JsonNode items = root.isArray() ? root
                    : root.path("data").path("candles").isArray() ? root.path("data").path("candles")
                    : root.path("candles");
            String currency = text(root, "currency");
            if (currency == null) {
                currency = text(root.path("data"), "currency");
            }
            List<CandlesResponse.Candle> candles = new ArrayList<>();
            if (items.isArray()) {
                for (JsonNode item : items) {
                    CandlesResponse.Candle candle = toCandle(item);
                    if (candle != null) {
                        candles.add(candle);
                    }
                }
            }
            candles.sort(Comparator.comparing(CandlesResponse.Candle::timestamp));
            if (candles.size() > limit) {
                candles = new ArrayList<>(candles.subList(candles.size() - limit, candles.size()));
            }
            return new CandlesResponse(symbol, currency, period.code(), List.copyOf(candles), Instant.now());
        } catch (Exception e) {
            log.warn("[CANDLES] unreadable answer for {}", symbol);
            throw new ModuleException(HttpStatus.SERVICE_UNAVAILABLE, "MKT-503",
                    "Chart data is not available right now. Please try again shortly");
        }
    }

    private static CandlesResponse.Candle toCandle(JsonNode item) {
        if (item == null || !item.isObject()) {
            return null;
        }
        Instant at = instant(first(item, "timestamp", "time", "t", "date"));
        BigDecimal open = number(first(item, "open", "o"));
        BigDecimal high = number(first(item, "high", "h"));
        BigDecimal low = number(first(item, "low", "l"));
        BigDecimal close = number(first(item, "close", "c"));
        if (at == null || open == null || high == null || low == null || close == null) {
            return null;
        }
        JsonNode volume = first(item, "volume", "v");
        return new CandlesResponse.Candle(at, open, high, low, close,
                volume != null && volume.isNumber() ? volume.asLong() : null);
    }

    private static JsonNode first(JsonNode node, String... names) {
        for (String name : names) {
            if (node.hasNonNull(name)) {
                return node.get(name);
            }
        }
        return null;
    }

    private static BigDecimal number(JsonNode node) {
        return node != null && node.isNumber() ? node.decimalValue() : null;
    }

    private static Instant instant(JsonNode node) {
        if (node == null) {
            return null;
        }
        try {
            if (node.isNumber()) {
                long value = node.asLong();
                return value > 100_000_000_000L ? Instant.ofEpochMilli(value) : Instant.ofEpochSecond(value);
            }
            String text = node.asText();
            return text.length() == 10 ? Instant.parse(text + "T00:00:00Z") : Instant.parse(text);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() || value.asText().isBlank() ? null : value.asText();
    }

    private static ModuleException invalid(String message) {
        return new ModuleException(HttpStatus.UNPROCESSABLE_ENTITY, "VAL-422", message);
    }
}
