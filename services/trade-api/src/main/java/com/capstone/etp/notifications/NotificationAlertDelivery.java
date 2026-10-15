package com.capstone.etp.notifications;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.UUID;


@Slf4j
@Service
@RequiredArgsConstructor
class NotificationAlertDelivery implements AlertDelivery {

    private final NotificationService notifications;

    @Override
    public DeliveryReceipt deliver(AlertDeliveryRequest request) throws AlertDeliveryException {
        try {
            NotificationService.Enqueued result = notifications.enqueue(
                    eventIdFor(request.alertId(), request.triggerId()),
                    request.accountId(),
                    NotificationKind.PRICE_ALERT,
                    subject(request),
                    body(request));
            return new DeliveryReceipt(result.notificationId(), !result.created());
        } catch (ContentGuard.UnsafeContentException e) {
            throw new AlertDeliveryException(e.getMessage(), e);
        } catch (RuntimeException e) {
            // The alert stays with the caller, who will try again; the customer asked to be told.
            log.error("[NOTIFY] alert {} could not be recorded for delivery: {}", request.alertId(), e.getMessage());
            throw new AlertDeliveryException("The alert could not be recorded for delivery", e);
        }
    }

    /** The same firing of the same alert always maps to the same id; a later firing is a different one. */
    static UUID eventIdFor(long alertId, long triggerId) {
        return UUID.nameUUIDFromBytes(("price-alert:" + alertId + ":" + triggerId).getBytes(StandardCharsets.UTF_8));
    }

    /** "is above", "equals" and so on: the condition in the words a customer would use. */
    static String phrase(AlertDeliveryRequest.Operator operator) {
        return switch (operator) {
            case GT -> "is above";
            case GTE -> "is at or above";
            case LT -> "is below";
            case LTE -> "is at or below";
            case EQ -> "equals";
        };
    }

    static String subject(AlertDeliveryRequest r) {
        return "Price alert: " + r.symbol() + " " + phrase(r.operator()) + " " + plain(r.threshold());
    }

    static String body(AlertDeliveryRequest r) {
        return r.symbol() + " " + phrase(r.operator()) + " " + plain(r.threshold())
                + ". The latest price is " + plain(r.price()) + ".";
    }

    private static String plain(BigDecimal value) {
        return value == null ? "n/a" : value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
