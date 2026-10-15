package com.capstone.etp.notifications;

import com.capstone.etp.alerts.AlertOperator;
import com.capstone.etp.security.AccountAuthorizer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** What the customer reads when a price alert fires, for every operator, and that a second firing is a second message. */
class PriceAlertNotificationTest {

    private InMemoryNotificationMapper ledger;
    private NotificationAlertDelivery delivery;

    @BeforeEach
    void setUp() {
        ledger = new InMemoryNotificationMapper();
        delivery = new NotificationAlertDelivery(new NotificationService(ledger, new AccountAuthorizer()));
    }

    private Notification fire(AlertDeliveryRequest.Operator operator, long triggerId, String price) {
        DeliveryReceipt receipt = delivery.deliver(new AlertDeliveryRequest(1L, 7L, triggerId, "AAPL", operator,
                new BigDecimal("230.50"), new BigDecimal(price), Instant.parse("2026-10-07T14:05:11Z")));
        return ledger.rows.get(receipt.notificationId());
    }

    @Test
    void everyOperatorHasItsOwnWording() {
        assertTrue(fire(AlertDeliveryRequest.Operator.GT, 1, "231").getBody().contains("AAPL is above 230.50"));
        assertTrue(fire(AlertDeliveryRequest.Operator.GTE, 2, "230.50").getBody().contains("AAPL is at or above 230.50"));
        assertTrue(fire(AlertDeliveryRequest.Operator.LT, 3, "229").getBody().contains("AAPL is below 230.50"));
        assertTrue(fire(AlertDeliveryRequest.Operator.LTE, 4, "230.50").getBody().contains("AAPL is at or below 230.50"));
    }

    @Test
    void theEqualsCaseSaysEquals() {
        Notification n = fire(AlertDeliveryRequest.Operator.EQ, 5, "230.50");

        assertEquals("Price alert: AAPL equals 230.50", n.getSubject());
        assertTrue(n.getBody().contains("AAPL equals 230.50"));
        assertTrue(n.getBody().contains("The latest price is 230.50"));
    }

    @Test
    void theMessageIsAPriceAlertForTheOwnerAndNeverShowsInternalCodes() {
        Notification n = fire(AlertDeliveryRequest.Operator.GTE, 6, "231.20");

        assertEquals(NotificationKind.PRICE_ALERT, n.getKind());
        assertEquals(1L, n.getAccountId());
        assertFalse(n.getBody().contains("GTE") || n.getSubject().contains("GTE"));
    }

    @Test
    void theSameFiringIsOneMessageButALaterFiringIsAnother() {
        fire(AlertDeliveryRequest.Operator.GTE, 8, "231");
        DeliveryReceipt again = delivery.deliver(new AlertDeliveryRequest(1L, 7L, 8, "AAPL", AlertDeliveryRequest.Operator.GTE,
                new BigDecimal("230.50"), new BigDecimal("231"), Instant.now()));
        assertTrue(again.duplicate());
        assertEquals(1, ledger.rows.size());

        fire(AlertDeliveryRequest.Operator.GTE, 9, "232");
        assertEquals(2, ledger.rows.size(), "an alert that was switched back on and fired again messages the customer again");
        assertNotEquals(NotificationAlertDelivery.eventIdFor(7L, 8L), NotificationAlertDelivery.eventIdFor(7L, 9L));
    }

    @Test
    void theInboxCanBeFilteredByType() {
        fire(AlertDeliveryRequest.Operator.GT, 10, "231");
        UUIDs.order(ledger, 1L);
        NotificationService service = new NotificationService(ledger, new AccountAuthorizer());
        com.capstone.etp.testsupport.TestIdentity.customer(1L);
        try {
            assertEquals(1, service.list(1L, false, null, "price_alert").size());
            assertEquals(1, service.list(1L, false, null, "order").size());
            assertEquals(2, service.list(1L, false, null, "all").size());
            assertEquals(2L, service.counts(1L).get("all"));
            assertEquals(1L, service.counts(1L).get("priceAlert"));
            assertEquals(1L, service.counts(1L).get("order"));
        } finally {
            com.capstone.etp.testsupport.TestIdentity.clear();
        }
    }

    @Test
    void readAllMarksOnlyThisAccountsMessagesAndSaysHowManyChanged() {
        fire(AlertDeliveryRequest.Operator.GT, 20, "231");
        UUIDs.order(ledger, 1L);
        UUIDs.order(ledger, 2L);
        NotificationService service = new NotificationService(ledger, new AccountAuthorizer());
        com.capstone.etp.testsupport.TestIdentity.customer(1L);
        try {
            assertEquals(2, service.markAllRead(1L));
            assertEquals(0, service.unreadCount(1L));
            assertEquals(0, service.markAllRead(1L), "nothing left to read");
            assertEquals(1, ledger.countUnread(2L), "another account's inbox is untouched");
        } finally {
            com.capstone.etp.testsupport.TestIdentity.clear();
        }
    }

    /** Puts an order message in the ledger without going through Kafka. */
    private static final class UUIDs {
        static void order(InMemoryNotificationMapper ledger, long accountId) {
            ledger.insertIfAbsent(java.util.UUID.randomUUID(), accountId, NotificationKind.ORDER_FILLED,
                    "Order filled: BUY 100 AAPL", "Your BUY order for 100 AAPL was filled at 230.50.");
        }
    }
}
