package com.capstone.etp.kafka.producer;

import com.capstone.etp.entity.Order;
import com.capstone.etp.kafka.models.KafkaMessageEnvelope;
import com.capstone.etp.kafka.models.OrderPlacedPayload;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderEventProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    private static final String ORDERS_TOPIC = "orders";
    private static final String SOURCE = "trade-api";
    private static final String EVENT_TYPE = "ORDER_PLACED";
    private static final int SCHEMA_VERSION = 1;

    
    public void publishOrderPlaced(Order order, String instrumentSymbol, BigDecimal convertedLimitPrice) {
        try {
            OrderPlacedPayload payload = OrderPlacedPayload.builder()
                    .orderId(order.getId())
                    .accountId(order.getAccountId())
                    .symbol(instrumentSymbol)
                    .side(order.getSide().toString())
                    .quantity(order.getQuantity())
                    .price(convertedLimitPrice)
                    .idempotencyKey(order.getIdempotencyKey().toString())
                    .createdOn(order.getCreatedAt())
                    .build();

            KafkaMessageEnvelope<OrderPlacedPayload> envelope = new KafkaMessageEnvelope<>(
                    UUID.randomUUID(),
                    EVENT_TYPE,
                    Instant.now(),
                    SOURCE,
                    SCHEMA_VERSION,
                    payload
            );

            // Explicit JSON serialization to match executor pattern
            String json = objectMapper.writeValueAsString(envelope);
            String key = order.getAccountId().toString();

            kafkaTemplate.send(ORDERS_TOPIC, key, json);

            log.info("Published ORDER_PLACED event for order {} to account {}",
                    order.getId(), order.getAccountId());

        } catch (Exception e) {
            log.error("Failed to publish ORDER_PLACED event for order {}: {}",
                    order.getId(), e.getMessage(), e);
            throw new RuntimeException("Failed to publish ORDER_PLACED event", e);
        }
    }
}
