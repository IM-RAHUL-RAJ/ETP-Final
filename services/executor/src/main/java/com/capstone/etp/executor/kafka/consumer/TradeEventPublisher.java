package com.capstone.etp.executor.kafka.consumer;

import com.capstone.etp.executor.kafka.models.KafkaMessageEnvelope;
import com.capstone.etp.executor.kafka.models.TradeEventPayload;
import com.capstone.etp.executor.service.SettlementResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;
//Builds and publishes an ORDER_FILLED or ORDER_REJECTED message to the trade-events topic, keyed by accountId.
@Slf4j
@Component
@RequiredArgsConstructor
public class TradeEventPublisher {

    private static final String SOURCE = "trade-executor";
    private static final int SCHEMA_VERSION = 1;

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.kafka.topics.trade-events:trade-events}")
    private String tradeEventsTopic;

    public void publish(SettlementResult result) {
        TradeEventPayload payload = new TradeEventPayload(
                result.orderId(),
                result.accountId(),
                result.symbol(),
                result.side(),
                result.quantity(),
                result.limitPrice(),
                result.executedPrice(),
                result.status() != null ? result.status().name() : null,
                result.reason(),
                result.cashDelta(),
                result.positionQuantityAfter(),
                result.averageCostAfter(),
                result.executedOn()
        );

        String eventType = "ORDER_REJECTED";
        if (result.status() != null) {
            switch (result.status()) {
                case FILLED    -> eventType = "ORDER_FILLED";
                case CANCELLED -> eventType = "ORDER_CANCELLED";
                default        -> eventType = "ORDER_REJECTED";
            }
        }

        KafkaMessageEnvelope<TradeEventPayload> envelope = new KafkaMessageEnvelope<>(
                UUID.randomUUID(),
                eventType,
                Instant.now(),
                SOURCE,
                SCHEMA_VERSION,
                payload
        );

        try {
            String json = objectMapper.writeValueAsString(envelope);
            String key  = String.valueOf(result.accountId());
            kafkaTemplate.send(tradeEventsTopic, key, json);
            log.info("Published {} for order {} to {}", eventType, result.orderId(), tradeEventsTopic);
        } catch (JsonProcessingException e) {
            // Serialisation of our own DTO should never fail; log and rethrow so the
            // consumer does not ack — the event will be retried.
            throw new RuntimeException("Failed to serialise trade-event for order " + result.orderId(), e);
        }
    }
}

