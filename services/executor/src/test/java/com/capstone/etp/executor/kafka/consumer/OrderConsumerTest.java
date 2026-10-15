package com.capstone.etp.executor.kafka.consumer;

import com.capstone.etp.executor.exception.DeadLetterException;
import com.capstone.etp.executor.kafka.models.KafkaMessageEnvelope;
import com.capstone.etp.executor.kafka.models.OrderPlacedPayload;
import com.capstone.etp.executor.service.ExecutionService;
import com.capstone.etp.executor.service.SettlementResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.Acknowledgment;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrderConsumerTest {

    private ExecutionService executionService;
    private TradeEventPublisher tradeEventPublisher;
    private DeadLetterPublisher deadLetterPublisher;
    private ObjectMapper objectMapper;
    private OrderConsumer consumer;

    @BeforeEach
    void setUp() {
        executionService = mock(ExecutionService.class);
        tradeEventPublisher = mock(TradeEventPublisher.class);
        deadLetterPublisher = mock(DeadLetterPublisher.class);
        objectMapper = new ObjectMapper().findAndRegisterModules();
        consumer = new OrderConsumer(executionService, tradeEventPublisher, deadLetterPublisher, objectMapper);
    }

    @Test
    void consume_happyPath_executesPublishesAndAcknowledges() throws Exception {
        ConsumerRecord<String, String> record = validRecord("ORDER_PLACED", validPayload());
        Acknowledgment ack = mock(Acknowledgment.class);
        SettlementResult result = settlementResult(false);

        when(executionService.execute(any())).thenReturn(result);

        consumer.consume(record, ack);

        verify(executionService).execute(any());
        verify(tradeEventPublisher).publish(result);
        verify(ack).acknowledge();
        verify(deadLetterPublisher, never()).publish(any(), any(), any());
    }

    @Test
    void consume_skipsPublish_whenAlreadyHandled() throws Exception {
        ConsumerRecord<String, String> record = validRecord("ORDER_PLACED", validPayload());
        Acknowledgment ack = mock(Acknowledgment.class);
        when(executionService.execute(any())).thenReturn(SettlementResult.alreadyHandled(UUID.randomUUID()));

        consumer.consume(record, ack);

        verify(tradeEventPublisher, never()).publish(any());
        verify(ack).acknowledge();
    }

    @Test
    void consume_deadLettersOnDeserializationFailure_andAcknowledges() {
        ConsumerRecord<String, String> record = new ConsumerRecord<>("orders", 0, 1L, "k", "not-json");
        Acknowledgment ack = mock(Acknowledgment.class);

        consumer.consume(record, ack);

        verify(deadLetterPublisher).publish(eq(record), eq("DESERIALIZATION_FAILURE"), any());
        verify(ack).acknowledge();
        verify(executionService, never()).execute(any());
    }

    @Test
    void consume_deadLettersOnUnexpectedEventType_andAcknowledges() throws Exception {
        ConsumerRecord<String, String> record = validRecord("QUOTE", validPayload());
        Acknowledgment ack = mock(Acknowledgment.class);

        consumer.consume(record, ack);

        verify(deadLetterPublisher).publish(eq(record), eq("UNEXPECTED_EVENT_TYPE"), eq(null));
        verify(ack).acknowledge();
    }

    @Test
    void consume_deadLettersOnMissingOrderId_andAcknowledges() throws Exception {
        OrderPlacedPayload payload = validPayload();
        payload.setOrderId(null);
        ConsumerRecord<String, String> record = validRecord("ORDER_PLACED", payload);
        Acknowledgment ack = mock(Acknowledgment.class);

        consumer.consume(record, ack);

        verify(deadLetterPublisher).publish(eq(record), eq("MISSING_ORDER_ID"), eq(null));
        verify(ack).acknowledge();
    }

    @Test
    void consume_deadLetterExceptionPath_deadLettersAndAcknowledges() throws Exception {
        ConsumerRecord<String, String> record = validRecord("ORDER_PLACED", validPayload());
        Acknowledgment ack = mock(Acknowledgment.class);
        when(executionService.execute(any())).thenThrow(new DeadLetterException("POISON_MESSAGE"));

        consumer.consume(record, ack);

        verify(deadLetterPublisher).publish(eq(record), eq("POISON_MESSAGE"), any(DeadLetterException.class));
        verify(ack).acknowledge();
    }

    @Test
    void consume_transientExceptionPath_rethrowsAndDoesNotAck() throws Exception {
        ConsumerRecord<String, String> record = validRecord("ORDER_PLACED", validPayload());
        Acknowledgment ack = mock(Acknowledgment.class);
        when(executionService.execute(any())).thenThrow(new RuntimeException("db down"));

        assertThrows(RuntimeException.class, () -> consumer.consume(record, ack));

        verify(ack, never()).acknowledge();
        verify(deadLetterPublisher, never()).publish(any(), any(), any());
    }

    private ConsumerRecord<String, String> validRecord(String eventType, OrderPlacedPayload payload) throws Exception {
        KafkaMessageEnvelope<OrderPlacedPayload> envelope = new KafkaMessageEnvelope<>(
                UUID.randomUUID(),
                eventType,
                Instant.now(),
                "trade-api",
                1,
                payload
        );
        String json = objectMapper.writeValueAsString(envelope);
        return new ConsumerRecord<>("orders", 0, 1L, "1001", json);
    }

    private static OrderPlacedPayload validPayload() {
        OrderPlacedPayload payload = new OrderPlacedPayload();
        payload.setOrderId(UUID.randomUUID());
        payload.setAccountId(1001L);
        payload.setSymbol("AAPL");
        payload.setSide("BUY");
        payload.setQuantity(10);
        payload.setPrice(new BigDecimal("100.00"));
        payload.setIdempotencyKey(UUID.randomUUID().toString());
        payload.setCreatedOn(Instant.now());
        return payload;
    }

    private static SettlementResult settlementResult(boolean alreadyHandled) {
        if (alreadyHandled) {
            return SettlementResult.alreadyHandled(UUID.randomUUID());
        }
        return new SettlementResult(
                UUID.randomUUID(),
                1001L,
                "AAPL",
                "BUY",
                10,
                new BigDecimal("100.00"),
                new BigDecimal("99.50"),
                com.capstone.etp.executor.enums.OrderStatus.FILLED,
                null,
                new BigDecimal("-995.00"),
                10,
                new BigDecimal("99.50"),
                Instant.now(),
                false
        );
    }
}
