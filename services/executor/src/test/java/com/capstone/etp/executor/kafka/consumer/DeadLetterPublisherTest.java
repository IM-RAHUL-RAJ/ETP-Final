package com.capstone.etp.executor.kafka.consumer;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class DeadLetterPublisherTest {

    @SuppressWarnings("unchecked")
    @Test
    void publish_writesDltTopicAndHeaders() {
        KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);
        DeadLetterPublisher publisher = new DeadLetterPublisher(kafkaTemplate);
        ConsumerRecord<String, String> record = new ConsumerRecord<>("orders", 0, 1L, "acc-1", "payload");

        publisher.publish(record, "BAD_PAYLOAD", new IllegalArgumentException("invalid"));

        ArgumentCaptor<ProducerRecord<String, String>> captor = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafkaTemplate).send(captor.capture());

        ProducerRecord<String, String> sent = captor.getValue();
        assertEquals("orders.DLT", sent.topic());
        assertEquals("acc-1", sent.key());
        assertEquals("payload", sent.value());

        assertEquals("BAD_PAYLOAD", asString(sent, "X-DLT-Reason"));
        assertEquals("java.lang.IllegalArgumentException", asString(sent, "X-DLT-Exception"));
        assertEquals("orders", asString(sent, "X-DLT-Original-Topic"));
        assertNotNull(asString(sent, "X-DLT-Timestamp"));
    }

    @SuppressWarnings("unchecked")
    @Test
    void publish_handlesNullCause() {
        KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);
        DeadLetterPublisher publisher = new DeadLetterPublisher(kafkaTemplate);
        ConsumerRecord<String, String> record = new ConsumerRecord<>("orders", 0, 2L, "acc-2", "payload-2");

        publisher.publish(record, "UNEXPECTED_EVENT_TYPE", null);

        ArgumentCaptor<ProducerRecord<String, String>> captor = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafkaTemplate).send(captor.capture());
        assertEquals("none", asString(captor.getValue(), "X-DLT-Exception"));
    }

    private static String asString(ProducerRecord<String, String> record, String key) {
        var header = record.headers().lastHeader(key);
        return header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
    }
}
