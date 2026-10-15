package com.capstone.etp.notifications;

import com.capstone.etp.config.JacksonConfig;
import com.capstone.etp.kafka.MalformedEventException;
import com.capstone.etp.security.AccountAuthorizer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TradeEventListenerTest {

    private static final UUID EVENT_ID = UUID.fromString("d47f9a10-3e2b-4c88-b0a1-7e6d5c4b3a29");

    private InMemoryNotificationMapper ledger;
    private TradeEventListener listener;

    @BeforeEach
    void setUp() {
        ledger = new InMemoryNotificationMapper();
        listener = new TradeEventListener(
                new JacksonConfig().objectMapper(),
                new NotificationComposer(),
                new NotificationService(ledger, new AccountAuthorizer()));
    }

    static String event(String type, UUID eventId, String body) {
        return """
                {"eventId":"%s","eventType":"%s","eventTime":"2026-10-06T09:14:24Z","source":"trade-executor",
                 "schemaVersion":1,"payload":{%s}}""".formatted(eventId, type, body);
    }

    static String filled(UUID eventId) {
        return event("ORDER_FILLED", eventId, """
                "orderId":"6f2b1c2a-6a1e-4a4f-9c0d-2f7a1b3c4d5e","accountId":1,"symbol":"AAPL","side":"BUY",
                "quantity":100,"price":233.00,"executedPrice":232.71,"status":"FILLED","reason":null,
                "cashDelta":-23271.00,"positionQuantityAfter":300,"averageCostAfter":229.83,
                "executedOn":"2026-10-06T09:14:24Z\"""");
    }

    static String rejected(UUID eventId, String reason) {
        return event("ORDER_REJECTED", eventId, """
                "orderId":"6f2b1c2a-6a1e-4a4f-9c0d-2f7a1b3c4d5e","accountId":1,"symbol":"AAPL","side":"BUY",
                "quantity":100,"price":233.00,"executedPrice":null,"status":"REJECTED","reason":"%s",
                "cashDelta":0,"positionQuantityAfter":0,"averageCostAfter":0,"executedOn":"2026-10-06T09:14:24Z\""""
                .formatted(reason));
    }

    private Collection<Notification> stored() {
        return ledger.rows.values();
    }

    @Test
    void aFilledOrderProducesANotification() {
        listener.handle(filled(EVENT_ID));

        Notification n = stored().iterator().next();
        assertEquals(1, stored().size());
        assertEquals(NotificationKind.ORDER_FILLED, n.getKind());
        assertEquals(1L, n.getAccountId());
        assertEquals(NotificationStatus.QUEUED, n.getStatus(), "recorded for delivery, not yet sent");
        assertTrue(n.getSubject().contains("BUY 100 AAPL"));
        assertTrue(n.getBody().contains("232.71"));
    }

    @Test
    void aRejectedOrderProducesANotificationThatSaysWhy() {
        listener.handle(rejected(UUID.randomUUID(), "INSUFFICIENT_FUNDS"));

        Notification n = stored().iterator().next();
        assertEquals(NotificationKind.ORDER_REJECTED, n.getKind());
        assertTrue(n.getBody().contains("not enough cash"), n.getBody());
        assertFalse(n.getBody().contains("INSUFFICIENT_FUNDS"), "a reason code is never shown raw");
    }

    @Test
    void aCancelledOrderProducesANotification() {
        listener.handle(event("ORDER_CANCELLED", UUID.randomUUID(), """
                "accountId":1,"symbol":"MSFT","side":"SELL","quantity":5,"price":300.00,"executedPrice":null,
                "status":"CANCELLED","reason":"CANCELLED_BY_CUSTOMER\""""));

        Notification n = stored().iterator().next();
        assertEquals(NotificationKind.ORDER_CANCELLED, n.getKind());
        assertTrue(n.getBody().contains("at your request"));
    }

    @Test
    void aReplayedEventDoesNotRecordASecondMessage() {
        listener.handle(filled(EVENT_ID));
        listener.handle(filled(EVENT_ID));
        listener.handle(filled(EVENT_ID));

        assertEquals(1, stored().size(), "Kafka is at-least-once: the second delivery must be a no-op");
    }

    @Test
    void differentEventsForTheSameOrderAreDistinct() {
        listener.handle(filled(UUID.randomUUID()));
        listener.handle(filled(UUID.randomUUID()));

        assertEquals(2, stored().size());
    }

    @Test
    void anEventTypeNobodyAsksAboutIsIgnored() {
        listener.handle(event("ORDER_PLACED", UUID.randomUUID(), "\"accountId\":1,\"symbol\":\"AAPL\",\"side\":\"BUY\",\"quantity\":1"));

        assertTrue(stored().isEmpty());
    }

    @Test
    void theMessageNeverCarriesInternalIdentifiers() {
        listener.handle(filled(EVENT_ID));

        Notification n = stored().iterator().next();
        String text = n.getSubject() + " " + n.getBody();
        assertFalse(text.contains("6f2b1c2a"), "order id leaked: " + text);
        assertFalse(text.contains(EVENT_ID.toString()), "event id leaked: " + text);
    }

    // ---------------------------------------------------------------- poison messages go to the DLT, not into the ledger

    @Test
    void notJsonIsMalformed() {
        assertThrows(MalformedEventException.class, () -> listener.handle("{not json"));
        assertTrue(stored().isEmpty());
    }

    @Test
    void aMissingAccountIsMalformed() {
        assertThrows(MalformedEventException.class, () -> listener.handle(event("ORDER_FILLED", UUID.randomUUID(),
                "\"symbol\":\"AAPL\",\"side\":\"BUY\",\"quantity\":1,\"executedPrice\":1.0")));
    }

    @Test
    void anInvalidEventIdIsMalformed() {
        assertThrows(MalformedEventException.class, () -> listener.handle(
                filled(EVENT_ID).replace(EVENT_ID.toString(), "not-a-uuid")));
    }

    @Test
    void aZeroQuantityIsMalformed() {
        assertThrows(MalformedEventException.class, () -> listener.handle(
                filled(EVENT_ID).replace("\"quantity\":100", "\"quantity\":0")));
    }

    @Test
    void anEventFromAnUnexpectedProducerIsRefused() {
        assertThrows(MalformedEventException.class, () -> listener.handle(
                filled(EVENT_ID).replace("trade-executor", "somebody-else")));
        assertTrue(stored().isEmpty());
    }

    @Test
    void anEventThatWouldPutAnAddressInTheMessageIsRefusedNotSent() {
        // A symbol such as an address would end up in the subject; the content guard refuses it.
        assertThrows(MalformedEventException.class, () -> listener.handle(
                filled(EVENT_ID).replace("\"symbol\":\"AAPL\"", "\"symbol\":\"someone@example.com\"")));
        assertTrue(stored().isEmpty());
    }
}
