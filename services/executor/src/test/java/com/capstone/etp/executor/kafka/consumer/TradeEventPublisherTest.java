package com.capstone.etp.executor.kafka.consumer;

import com.capstone.etp.executor.enums.OrderStatus;
import com.capstone.etp.executor.service.SettlementResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TradeEventPublisherTest {

    private KafkaTemplate<String, String> kafkaTemplate;
    private ObjectMapper objectMapper;
    private TradeEventPublisher publisher;

    @BeforeEach
    void setUp() {
        kafkaTemplate = mock(KafkaTemplate.class);
        objectMapper = new ObjectMapper().findAndRegisterModules();
        publisher = new TradeEventPublisher(kafkaTemplate, objectMapper);
        setField(publisher, "tradeEventsTopic", "trade-events-test");
    }

    @Test
    void publish_mapsFilledToOrderFilledEvent() throws Exception {
        SettlementResult result = settlementResult(OrderStatus.FILLED, null);

        publisher.publish(result);

        ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(eq("trade-events-test"), eq("1001"), jsonCaptor.capture());
        JsonNode node = objectMapper.readTree(jsonCaptor.getValue());
        assertEquals("ORDER_FILLED", node.get("eventType").asText());
    }

    @Test
    void publish_mapsCancelledToOrderCancelledEvent() throws Exception {
        SettlementResult result = settlementResult(OrderStatus.CANCELLED, "cancelled");

        publisher.publish(result);

        ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(eq("trade-events-test"), eq("1001"), jsonCaptor.capture());
        JsonNode node = objectMapper.readTree(jsonCaptor.getValue());
        assertEquals("ORDER_CANCELLED", node.get("eventType").asText());
    }

    @Test
    void publish_throwsRuntimeException_whenSerializationFails() throws Exception {
        ObjectMapper failingMapper = mock(ObjectMapper.class);
        when(failingMapper.writeValueAsString(org.mockito.ArgumentMatchers.any()))
                .thenThrow(new com.fasterxml.jackson.core.JsonProcessingException("boom") {});
        TradeEventPublisher failingPublisher = new TradeEventPublisher(kafkaTemplate, failingMapper);
        setField(failingPublisher, "tradeEventsTopic", "trade-events-test");

        assertThrows(RuntimeException.class, () -> failingPublisher.publish(settlementResult(OrderStatus.REJECTED, "x")));
    }

    private static SettlementResult settlementResult(OrderStatus status, String reason) {
        return new SettlementResult(
                UUID.randomUUID(),
                1001L,
                "AAPL",
                "BUY",
                10,
                new BigDecimal("100.00"),
                status == OrderStatus.FILLED ? new BigDecimal("99.50") : null,
                status,
                reason,
                BigDecimal.ZERO,
                10,
                new BigDecimal("99.50"),
                Instant.now(),
                false
        );
    }

    private static void setField(Object target, String fieldName, Object value) {
        try {
            var field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set field " + fieldName, e);
        }
    }
}
