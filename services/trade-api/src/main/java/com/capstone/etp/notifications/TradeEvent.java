package com.capstone.etp.notifications;

import com.capstone.etp.kafka.MalformedEventException;
import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;

/**
 * The part of a {@code trade-events} message this module uses, read defensively. Anything that is
 * missing or out of range makes the message malformed: it will never succeed on retry, so it is
 * dead-lettered on the first attempt.
 */
record TradeEvent(
        UUID eventId,
        String eventType,
        String source,
        long accountId,
        String symbol,
        String side,
        int quantity,
        BigDecimal price,
        BigDecimal executedPrice,
        String reason,
        UUID orderId) {

    static final Set<String> HANDLED_TYPES = Set.of("ORDER_FILLED", "ORDER_REJECTED", "ORDER_CANCELLED");

    /** Producers that may write order outcomes to the topic. */
    static final Set<String> TRUSTED_SOURCES = Set.of("trade-executor", "trade-api");

    static TradeEvent from(JsonNode root) {
        if (root == null || !root.isObject()) {
            throw new MalformedEventException("Event is not a JSON object");
        }
        UUID eventId = uuid(text(root, "eventId"));
        String eventType = text(root, "eventType");
        JsonNode payload = root.get("payload");
        if (payload == null || !payload.isObject()) {
            throw new MalformedEventException("Event has no payload");
        }

        JsonNode account = payload.get("accountId");
        if (account == null || !account.canConvertToLong() || account.asLong() < 1) {
            throw new MalformedEventException("Event has no valid accountId");
        }
        String symbol = text(payload, "symbol");
        String side = text(payload, "side");
        if (!side.equals("BUY") && !side.equals("SELL")) {
            throw new MalformedEventException("Event has an unknown side");
        }
        JsonNode quantity = payload.get("quantity");
        if (quantity == null || !quantity.canConvertToInt() || quantity.asInt() < 1) {
            throw new MalformedEventException("Event has no valid quantity");
        }

        return new TradeEvent(
                eventId,
                eventType,
                root.hasNonNull("source") ? root.get("source").asText() : "",
                account.asLong(),
                symbol,
                side,
                quantity.asInt(),
                decimal(payload, "price"),
                decimal(payload, "executedPrice"),
                payload.hasNonNull("reason") ? payload.get("reason").asText() : null,
                optionalUuid(payload, "orderId"));
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull() || !value.isTextual() || value.asText().isBlank()) {
            throw new MalformedEventException("Event has no " + field);
        }
        return value.asText();
    }

    private static UUID uuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            throw new MalformedEventException("Event has an invalid eventId", e);
        }
    }

    private static UUID optionalUuid(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual()) {
            return null;
        }
        try {
            return UUID.fromString(value.asText());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static BigDecimal decimal(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() || !value.isNumber() ? null : value.decimalValue();
    }
}
