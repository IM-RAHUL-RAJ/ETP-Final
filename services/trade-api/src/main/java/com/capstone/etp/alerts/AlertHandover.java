package com.capstone.etp.alerts;

import com.capstone.etp.notifications.AlertDelivery;
import com.capstone.etp.notifications.AlertDeliveryRequest;
import com.capstone.etp.notifications.DeliveryReceipt;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;


@Slf4j
@Component
class AlertHandover {

    private final AlertDelivery delivery;
    private final AlertMapper mapper;

    AlertHandover(AlertDelivery delivery, AlertMapper mapper) {
        this.delivery = delivery;
        this.mapper = mapper;
    }

    /** @return true when notifications has the message */
    boolean handOver(long alertId, long accountId, String symbol, AlertOperator operator,
                     BigDecimal threshold, BigDecimal price, Instant observedAt) {
        try {
            long triggerId = currentTriggerId(alertId, price);
            DeliveryReceipt receipt = delivery.deliver(new AlertDeliveryRequest(
                    accountId,
                    alertId,
                    triggerId,
                    symbol,
                    AlertDeliveryRequest.Operator.valueOf(operator.name()),
                    threshold,
                    price,
                    observedAt));
            mapper.attachNotification(triggerId, receipt.notificationId());
            mapper.markHandedOver(alertId);
            log.info("[ALERTS] alert {} handed to notifications", alertId);
            return true;
        } catch (RuntimeException e) {
            log.error("[ALERTS] hand-over of alert {} failed ({}); it will be retried",
                    alertId, e.getClass().getSimpleName());
            try {
                mapper.markHandoverFailed(alertId);
            } catch (RuntimeException inner) {
                // The alert stays PENDING_HANDOVER, which the retrier also picks up.
                log.error("[ALERTS] could not record the failed hand-over of alert {}", alertId);
            }
            return false;
        }
    }

    /** The firing being delivered. A legacy alert that fired before history existed gets its row now. */
    private long currentTriggerId(long alertId, BigDecimal price) {
        Long latest = mapper.findLatestTriggerId(alertId);
        if (latest == null) {
            mapper.insertTrigger(alertId, UUID.randomUUID(), price);
            latest = mapper.findLatestTriggerId(alertId);
        }
        if (latest == null) {
            throw new IllegalStateException("No trigger row for alert " + alertId);
        }
        return latest;
    }
}
