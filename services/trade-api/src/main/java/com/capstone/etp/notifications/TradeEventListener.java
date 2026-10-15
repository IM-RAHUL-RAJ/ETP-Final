package com.capstone.etp.notifications;

import com.capstone.etp.kafka.MalformedEventException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.capstone.etp.kafka.EventJson;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumes {@code trade-events} in the {@code notification-service} group and turns the three order
 * outcomes (filled, rejected, cancelled) into recorded notifications.
 *
 * <p><b>Consumption and delivery are separate.</b> This method returns, and the container commits the
 * offset, as soon as the notification is durably recorded as QUEUED. It never waits for an e-mail or
 * SMS provider, so a provider outage cannot stall the partition and back up every account on it.
 * Whether the message reached the customer is the notification's own status, tracked by the
 * dispatcher.
 *
 * <p><b>Duplicates.</b> Kafka is at-least-once. The event id is the ledger's idempotency key, so a
 * redelivered or replayed event records nothing a second time.
 *
 * <p><b>Failures.</b> A malformed event throws {@link MalformedEventException}: it will never succeed,
 * so the container's error handler dead-letters it immediately. A database failure propagates, the
 * offset is not committed, and the event is retried and finally dead-lettered, never skipped.
 *
 * <p><b>Authorisation.</b> There is no caller and no token here: the listener acts on a message from an
 * internal topic. It reads nothing on a customer's behalf and exposes nothing; it only writes a
 * notification for the account named in the event, which the customer can later read only through the
 * authorised history routes. Only the platform's own producers are accepted ({@link TradeEvent#TRUSTED_SOURCES}).
 */
@Slf4j
@Component
@RequiredArgsConstructor
class TradeEventListener {

    private final ObjectMapper objectMapper;
    private final NotificationComposer composer;
    private final NotificationService notifications;

    @KafkaListener(
            topics = "${app.kafka.topics.trade-events:trade-events}",
            groupId = "${app.kafka.groups.notifications:notification-service}",
            autoStartup = "${app.notifications.consumer.enabled:true}",
            properties = "auto.offset.reset=latest")
    public void onMessage(String message) {
        handle(message);
    }

    /** Package-visible so tests can drive it without a broker. */
    void handle(String message) {
        TradeEvent event;
        try {
            event = TradeEvent.from(EventJson.parse(objectMapper, message));
        } catch (JsonProcessingException e) {
            throw new MalformedEventException("Event is not valid JSON", e);
        }

        if (!TradeEvent.HANDLED_TYPES.contains(event.eventType())) {
            log.debug("[NOTIFY] ignoring event type {}", event.eventType());
            return;
        }
        if (!TradeEvent.TRUSTED_SOURCES.contains(event.source())) {
            throw new MalformedEventException("Event from an unexpected source");
        }

        NotificationComposer.Draft draft = composer.compose(event.eventType(), event);
        try {
            notifications.enqueue(event.eventId(), event.accountId(), draft.kind(), draft.subject(), draft.body());
        } catch (ContentGuard.UnsafeContentException e) {
            // A message that carries something it must not will never become acceptable on retry.
            throw new MalformedEventException(e.getMessage(), e);
        }
    }
}
