package com.capstone.etp.executor.kafka.consumer;

import com.capstone.etp.executor.exception.DeadLetterException;
import com.capstone.etp.executor.kafka.models.KafkaMessageEnvelope;
import com.capstone.etp.executor.kafka.models.OrderPlacedPayload;
import com.capstone.etp.executor.service.ExecutionService;
import com.capstone.etp.executor.service.SettlementResult;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

//Consumes messages from the 'orders' topic, delegates execution to ExecutionService,
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderConsumer {

    private static final String EXPECTED_EVENT_TYPE = "ORDER_PLACED";

    private final ExecutionService executionService;
    private final TradeEventPublisher tradeEventPublisher;
    private final DeadLetterPublisher deadLetterPublisher;
    private final ObjectMapper objectMapper;

    @KafkaListener(
        topics    = "${app.kafka.topics.orders:orders}",
        groupId   = "${app.kafka.consumer-group:trade-executor}",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume(ConsumerRecord<String, String> record, Acknowledgment ack) {
        log.debug("Received message — topic={} partition={} offset={} key={}",
                record.topic(), record.partition(), record.offset(), record.key());

        // 1. Deserialise
        KafkaMessageEnvelope<OrderPlacedPayload> envelope;
        try {
            envelope = objectMapper.readValue(record.value(),
                    new TypeReference<KafkaMessageEnvelope<OrderPlacedPayload>>() {});
        } catch (Exception e) {
            log.error("Deserialisation failure — dead-lettering: {}", e.getMessage());
            deadLetterPublisher.publish(record, "DESERIALIZATION_FAILURE", e);
            ack.acknowledge();
            return;
        }

        // 2. Validate envelope
        if (!EXPECTED_EVENT_TYPE.equals(envelope.getEventType())) {
            log.warn("Unexpected eventType '{}' — dead-lettering", envelope.getEventType());
            deadLetterPublisher.publish(record, "UNEXPECTED_EVENT_TYPE", null);
            ack.acknowledge();
            return;
        }

        OrderPlacedPayload payload = envelope.getPayload();
        if (payload == null || payload.getOrderId() == null) {
            log.error("Null payload or orderId — dead-lettering");
            deadLetterPublisher.publish(record, "MISSING_ORDER_ID", null);
            ack.acknowledge();
            return;
        }

        // 3. Execute (DB transaction inside)
        try {
            SettlementResult result = executionService.execute(payload);

            // 4. Publish trade-event (after DB commit, before ack)
            if (!result.alreadyHandled()) {
                tradeEventPublisher.publish(result);
            } else {
                log.info("Order {} already handled — skipping trade-event publish", payload.getOrderId());
            }

            // 5. Acknowledge offset
            ack.acknowledge();

        } catch (DeadLetterException e) {
            log.error("Non-retryable failure for order {}: {}", payload.getOrderId(), e.getMessage());
            deadLetterPublisher.publish(record, e.getMessage(), e);
            ack.acknowledge();
        } catch (Exception e) {
            // Transient failure (DB down, lock budget exhausted, …)
            // Do NOT ack — let Spring Kafka retry on the next poll.
            log.error("Transient failure processing order {}: {}", payload.getOrderId(), e.getMessage(), e);
            throw new RuntimeException(
                    "Transient failure processing order " + payload.getOrderId(), e);
        }
    }
}

