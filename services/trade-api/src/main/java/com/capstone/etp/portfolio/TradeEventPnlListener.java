package com.capstone.etp.portfolio;

import com.capstone.etp.kafka.MalformedEventException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.capstone.etp.kafka.EventJson;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
class TradeEventPnlListener {

    private final ObjectMapper objectMapper;
    private final RealisedPnlBook book;

    @KafkaListener(
            topics = "${app.kafka.topics.trade-events:trade-events}",
            groupId = "${app.kafka.groups.portfolio:portfolio-service}",
            autoStartup = "${app.portfolio.consumer.enabled:true}",
            properties = "auto.offset.reset=earliest")
    public void onMessage(String message) {
        handle(message);
    }

    void handle(String message) {
        JsonNode root;
        try {
            // Money is read as exact decimals, never as doubles.
            root = EventJson.parse(objectMapper, message);
        } catch (JsonProcessingException e) {
            throw new MalformedEventException("Event is not valid JSON", e);
        }
        if (root == null || !root.isObject()) {
            throw new MalformedEventException("Event is not a JSON object");
        }
        if (!"ORDER_FILLED".equals(root.path("eventType").asText())) {
            return;
        }
        JsonNode payload = root.path("payload");
        if (!"SELL".equals(payload.path("side").asText())) {
            return;
        }

        UUID eventId = uuid(root.path("eventId").asText());
        long accountId = requireLong(payload, "accountId");
        String symbol = requireText(payload, "symbol");
        int quantity = (int) requireLong(payload, "quantity");
        BigDecimal executedPrice = requireDecimal(payload, "executedPrice");
        BigDecimal averageCost = requireDecimal(payload, "averageCostAfter");
        if (quantity < 1 || executedPrice.signum() <= 0 || averageCost.signum() < 0) {
            throw new MalformedEventException("Sale carries values that cannot be booked");
        }

        book.bookSale(eventId, accountId, symbol, quantity, executedPrice, averageCost, bookedAt(root, payload));
    }

    private static Instant bookedAt(JsonNode root, JsonNode payload) {
        for (String text : new String[]{payload.path("executedOn").asText(""), root.path("eventTime").asText("")}) {
            try {
                if (!text.isBlank()) {
                    return Instant.parse(text);
                }
            } catch (RuntimeException ignored) {
                // try the next one
            }
        }
        throw new MalformedEventException("Sale has no usable time");
    }

    private static UUID uuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            throw new MalformedEventException("Event has an invalid eventId", e);
        }
    }

    private static long requireLong(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.canConvertToLong()) {
            throw new MalformedEventException("Event has no " + field);
        }
        return value.asLong();
    }

    private static String requireText(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual() || value.asText().isBlank()) {
            throw new MalformedEventException("Event has no " + field);
        }
        return value.asText();
    }

    private static BigDecimal requireDecimal(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isNumber()) {
            throw new MalformedEventException("Event has no " + field);
        }
        return value.decimalValue();
    }
}
