package com.capstone.etp.notifications;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A customer must be able to tell, from the message alone, that a trade was placed by their strategy and not by them. */
class AutomatedOrderNotificationTest {

    private static final UUID AUTOMATED = UUID.randomUUID();

    private static TradeEvent event(String type, UUID orderId) {
        return new TradeEvent(UUID.randomUUID(), type, "trade-executor", 1L, "AAPL", "BUY", 10,
                new BigDecimal("232.80"), new BigDecimal("232.71"), "INSUFFICIENT_FUNDS", orderId);
    }

    private final NotificationComposer composer = new NotificationComposer(
            orderId -> AUTOMATED.equals(orderId) ? Optional.of("your moving-average crossover strategy #5 on AAPL") : Optional.empty());

    @Test
    void anAutomatedFillSaysSoInTheSubjectAndTheMessage() {
        NotificationComposer.Draft draft = composer.compose("ORDER_FILLED", event("ORDER_FILLED", AUTOMATED));

        assertEquals("Automated order filled: BUY 10 AAPL", draft.subject());
        assertTrue(draft.body().startsWith("This trade was placed automatically by your moving-average crossover strategy #5 on AAPL."), draft.body());
        assertTrue(draft.body().contains("was filled at 232.71"), draft.body());
    }

    @Test
    void anAutomatedRejectionSaysSoToo() {
        NotificationComposer.Draft draft = composer.compose("ORDER_REJECTED", event("ORDER_REJECTED", AUTOMATED));

        assertEquals("Automated order rejected: BUY 10 AAPL", draft.subject());
        assertTrue(draft.body().contains("placed automatically"), draft.body());
    }

    @Test
    void anOrderTheCustomerPlacedThemselvesIsUnchanged() {
        NotificationComposer.Draft draft = composer.compose("ORDER_FILLED", event("ORDER_FILLED", UUID.randomUUID()));

        assertEquals("Order filled: BUY 10 AAPL", draft.subject());
        assertTrue(draft.body().startsWith("Your BUY order"), draft.body());
    }

    @Test
    void anEventWithoutAnOrderIdIsTreatedAsAManualOrder() {
        NotificationComposer.Draft draft = composer.compose("ORDER_FILLED", event("ORDER_FILLED", null));

        assertEquals("Order filled: BUY 10 AAPL", draft.subject());
    }

    @Test
    void theOrderIdIsReadFromTheEventPayload() throws Exception {
        String json = "{\"eventId\":\"" + UUID.randomUUID() + "\",\"eventType\":\"ORDER_FILLED\",\"source\":\"trade-executor\","
                + "\"payload\":{\"orderId\":\"" + AUTOMATED + "\",\"accountId\":1,\"symbol\":\"AAPL\",\"side\":\"BUY\",\"quantity\":10,\"executedPrice\":232.71}}";

        TradeEvent parsed = TradeEvent.from(new com.fasterxml.jackson.databind.ObjectMapper().readTree(json));

        assertEquals(AUTOMATED, parsed.orderId());
    }
}
