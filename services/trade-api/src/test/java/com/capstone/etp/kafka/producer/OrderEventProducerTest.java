package com.capstone.etp.kafka.producer;

import com.capstone.etp.entity.Order;
import com.capstone.etp.enums.OrderSide;
import com.capstone.etp.kafka.models.KafkaMessageEnvelope;
import com.capstone.etp.kafka.models.OrderPlacedPayload;
import com.fasterxml.jackson.core.type.TypeReference;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrderEventProducerTest {

    private KafkaTemplate<String, String> kafkaTemplate;
    private ObjectMapper objectMapper;
    private OrderEventProducer producer;

    @BeforeEach
    void setUp() {
        kafkaTemplate = mock(KafkaTemplate.class);
        objectMapper = new ObjectMapper().findAndRegisterModules();
        producer = new OrderEventProducer(kafkaTemplate, objectMapper);
    }

    @Test
    void publishOrderPlaced_buildsEnvelopeAndPublishes() throws Exception {
        Order order = Order.builder()
                .id(UUID.randomUUID())
                .accountId(42L)
                .side(OrderSide.BUY)
                .quantity(10)
                .limitPrice(new BigDecimal("123.45"))
                .idempotencyKey(UUID.randomUUID())
                .createdAt(Instant.parse("2026-01-01T00:00:00Z"))
                .build();

        producer.publishOrderPlaced(order, "INFY.NS", new BigDecimal("101.23"));

        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(eq("orders"), eq("42"), payloadCaptor.capture());

        KafkaMessageEnvelope<OrderPlacedPayload> envelope = objectMapper.readValue(
                payloadCaptor.getValue(), new TypeReference<>() {
                });
        assertEquals("ORDER_PLACED", envelope.getEventType());
        assertEquals("trade-api", envelope.getSource());
        assertEquals("INFY.NS", envelope.getPayload().getSymbol());
        assertEquals("BUY", envelope.getPayload().getSide());
        assertEquals(new BigDecimal("101.23"), envelope.getPayload().getPrice());
        assertEquals(order.getId().toString(), envelope.getPayload().getOrderId().toString());
    }

    @Test
    void publishOrderPlaced_wrapsSerializationFailure() throws Exception {
        ObjectMapper failingMapper = mock(ObjectMapper.class);
        when(failingMapper.writeValueAsString(any()))
                .thenThrow(new RuntimeException("cannot serialize"));
        OrderEventProducer failingProducer = new OrderEventProducer(kafkaTemplate, failingMapper);

        Order order = Order.builder()
                .id(UUID.randomUUID())
                .accountId(42L)
                .side(OrderSide.SELL)
                .quantity(1)
                .limitPrice(BigDecimal.ONE)
                .idempotencyKey(UUID.randomUUID())
                .createdAt(Instant.now())
                .build();

        assertThrows(RuntimeException.class, () -> failingProducer.publishOrderPlaced(
                order,
                "AAPL",
                new BigDecimal("1.00")));
    }
}
