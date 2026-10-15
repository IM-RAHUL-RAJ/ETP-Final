package com.capstone.etp.notifications;

import com.capstone.etp.security.AccountAuthorizer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class NotificationAlertDeliveryTest {

    private InMemoryNotificationMapper ledger;
    private NotificationAlertDelivery delivery;

    @BeforeEach
    void setUp() {
        ledger = new InMemoryNotificationMapper();
        delivery = new NotificationAlertDelivery(new NotificationService(ledger, new AccountAuthorizer()));
    }

    private static AlertDeliveryRequest request(long alertId) {
        return new AlertDeliveryRequest(1L, alertId, 1L, "AAPL", AlertDeliveryRequest.Operator.GTE,
                new BigDecimal("235.00"), new BigDecimal("235.40"), Instant.parse("2026-10-06T09:15:00Z"));
    }

    @Test
    void anAlertBecomesAPriceAlertNotificationForItsOwner() {
        DeliveryReceipt receipt = delivery.deliver(request(7L));

        Notification n = ledger.rows.get(receipt.notificationId());
        assertFalse(receipt.duplicate());
        assertEquals(NotificationKind.PRICE_ALERT, n.getKind());
        assertEquals(1L, n.getAccountId());
        assertEquals(NotificationStatus.QUEUED, n.getStatus(), "durably recorded for delivery");
        assertTrue(n.getBody().contains("AAPL is at or above 235.00"));
        assertTrue(n.getBody().contains("235.40"));
    }

    @Test
    void handingTheSameAlertOverTwiceMessagesTheCustomerOnce() {
        DeliveryReceipt first = delivery.deliver(request(7L));
        DeliveryReceipt second = delivery.deliver(request(7L));

        assertEquals(1, ledger.rows.size());
        assertTrue(second.duplicate());
        assertEquals(first.notificationId(), second.notificationId());
    }

    @Test
    void differentAlertsAreDifferentMessages() {
        delivery.deliver(request(7L));
        delivery.deliver(request(8L));

        assertEquals(2, ledger.rows.size());
        assertNotEquals(NotificationAlertDelivery.eventIdFor(7L, 1L), NotificationAlertDelivery.eventIdFor(8L, 1L));
    }

    @Test
    void aLessThanAlertReadsAsBelow() {
        AlertDeliveryRequest below = new AlertDeliveryRequest(1L, 9L, 1L, "MSFT", AlertDeliveryRequest.Operator.LT,
                new BigDecimal("300"), new BigDecimal("299.5"), Instant.now());

        Notification n = ledger.rows.get(delivery.deliver(below).notificationId());

        assertTrue(n.getBody().contains("MSFT is below 300.00"));
    }

    @Test
    void whenTheMessageCannotBeRecordedItRaisesSoTheCallerKeepsTheAlert() {
        NotificationMapper broken = mock(NotificationMapper.class);
        when(broken.insertIfAbsent(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenThrow(new IllegalStateException("database down"));
        NotificationAlertDelivery failing = new NotificationAlertDelivery(new NotificationService(broken, new AccountAuthorizer()));

        assertThrows(AlertDeliveryException.class, () -> failing.deliver(request(7L)));
    }

    @Test
    void deliveryIsAJavaInterfaceNotAnHttpRoute() {
        // Publishing it as a route would let a customer's token send a message of its choosing.
        // No controller in the notifications package may accept an AlertDeliveryRequest,
        // and nothing outside the module may reach a controller method named like it.
        List<Class<?>> controllers = List.of(NotificationController.class);
        for (Class<?> controller : controllers) {
            assertTrue(controller.isAnnotationPresent(RestController.class));
            for (Method method : controller.getDeclaredMethods()) {
                assertFalse(Arrays.asList(method.getParameterTypes()).contains(AlertDeliveryRequest.class),
                        controller.getSimpleName() + "." + method.getName() + " takes an alert delivery request");
                assertFalse(method.getName().toLowerCase().contains("deliver") || method.getName().toLowerCase().contains("send"),
                        controller.getSimpleName() + "." + method.getName() + " looks like a send route");
            }
        }
        assertFalse(AlertDelivery.class.isAnnotationPresent(RestController.class));
    }
}
