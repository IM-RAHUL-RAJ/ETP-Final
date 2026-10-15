package com.capstone.etp.portfolio;

import com.capstone.etp.kafka.EventJson;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalInt;


@Slf4j
@Component
class FauxnanceQuoteClient implements QuoteProvider {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    FauxnanceQuoteClient(
            @Value("${app.fauxnance.base-url}") String baseUrl,
            @Value("${app.fauxnance.api-key:}") String apiKey,
            @Value("${app.fauxnance.connect-timeout-ms:2000}") int connectTimeoutMs,
            @Value("${app.fauxnance.read-timeout-ms:4000}") int readTimeoutMs,
            ObjectMapper objectMapper) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofMillis(connectTimeoutMs));
        requestFactory.setReadTimeout(Duration.ofMillis(readTimeoutMs));

        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .defaultHeader("X-API-Key", apiKey == null ? "" : apiKey)
                .build();
        this.objectMapper = objectMapper;
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("[PORTFOLIO] FAUXNANCE_API_KEY is not set; pricing will fall back to the market-data topic only");
        }
    }

    @Override
    public Map<String, ProviderQuote> fetch(List<String> symbols) throws QuoteProviderException {
        if (symbols == null || symbols.isEmpty()) {
            return Map.of();
        }
        if (symbols.size() > MAX_BATCH) {
            throw new IllegalArgumentException("At most " + MAX_BATCH + " symbols per call");
        }
        String body;
        try {
            body = restClient.get()
                    .uri("/quotes?symbols={symbols}", String.join(",", symbols))
                    .retrieve()
                    .body(String.class);
        } catch (RestClientException e) {
            // The message can include the URL; keep the type only.
            throw new QuoteProviderException("Fauxnance quote call failed: " + e.getClass().getSimpleName(), e);
        }
        return parse(body);
    }

    OptionalInt usageRemaining() throws QuoteProviderException {
        String body;
        try {
            body = restClient.get().uri("/usage").retrieve().body(String.class);
        } catch (RestClientException e) {
            throw new QuoteProviderException("Fauxnance usage call failed: " + e.getClass().getSimpleName(), e);
        }
        try {
            JsonNode root = objectMapper.readTree(body == null ? "{}" : body);
            for (JsonNode candidate : List.of(root, root.path("data"), root.path("quota"), root.path("data").path("quota"))) {
                for (String field : List.of("quotaRemaining", "remaining", "requestsRemaining")) {
                    if (candidate.path(field).isNumber()) {
                        return OptionalInt.of(candidate.path(field).asInt());
                    }
                }
            }
            return OptionalInt.empty();
        } catch (Exception e) {
            return OptionalInt.empty();
        }
    }

    Map<String, ProviderQuote> parse(String body) throws QuoteProviderException {
        if (body == null || body.isBlank()) {
            return Map.of();
        }
        try {
            JsonNode root = EventJson.parse(objectMapper, body);
            JsonNode items = root.isArray() ? root : root.path("data").path("quotes");
            if (!items.isArray()) {
                return Map.of();
            }
            Map<String, ProviderQuote> quotes = new HashMap<>();
            for (JsonNode item : items) {
                ProviderQuote quote = toQuote(item);
                if (quote != null) {
                    quotes.put(quote.symbol(), quote);
                }
            }
            return quotes;
        } catch (Exception e) {
            throw new QuoteProviderException("Fauxnance answered with something unreadable", e);
        }
    }

    private static ProviderQuote toQuote(JsonNode item) {
        if (item == null || !item.isObject() || (item.hasNonNull("error"))) {
            return null;
        }
        JsonNode quote = item.hasNonNull("quote") ? item.get("quote") : item;
        JsonNode price = quote.get("price");
        if (price == null || !price.isNumber() || price.decimalValue().signum() <= 0) {
            return null;
        }
        String symbol = text(quote, "symbol");
        if (symbol == null) {
            symbol = text(item, "symbol");
        }
        if (symbol == null) {
            return null;
        }
        boolean stale = item.path("stale").asBoolean(false) || quote.path("stale").asBoolean(false)
                || item.path("meta").path("stale").asBoolean(false);
        return new ProviderQuote(
                symbol.trim().toUpperCase(Locale.ROOT),
                price.decimalValue(),
                text(quote, "currency"),
                instant(quote.hasNonNull("asOf") ? quote.get("asOf") : quote.get("quoteAsOf")),
                stale,
                previousClose(quote, price.decimalValue()));
    }

    /** The previous close, read from either a previousClose field or "price minus change". Null when neither is there. */
    private static BigDecimal previousClose(JsonNode quote, BigDecimal price) {
        JsonNode direct = quote.get("previousClose");
        if (direct != null && direct.isNumber() && direct.decimalValue().signum() > 0) {
            return direct.decimalValue();
        }
        JsonNode change = quote.get("change");
        if (change != null && change.isNumber()) {
            BigDecimal derived = price.subtract(change.decimalValue());
            return derived.signum() > 0 ? derived : null;
        }
        return null;
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() || value.asText().isBlank() ? null : value.asText();
    }

    private static Instant instant(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        try {
            return Instant.parse(node.asText());
        } catch (RuntimeException e) {
            return null;
        }
    }
}
