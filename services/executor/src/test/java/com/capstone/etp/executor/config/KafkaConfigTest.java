package com.capstone.etp.executor.config;

import com.capstone.etp.executor.kafka.consumer.DeadLetterPublisher;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DefaultErrorHandler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;

class KafkaConfigTest {

    @Test
    void kafkaErrorHandler_buildsDefaultErrorHandler() {
        KafkaConfig config = new KafkaConfig();
        DeadLetterPublisher deadLetterPublisher = mock(DeadLetterPublisher.class);

        CommonErrorHandler handler = config.kafkaErrorHandler(deadLetterPublisher, 3, 1L, 2.0, 5L);

        assertNotNull(handler);
        assertInstanceOf(DefaultErrorHandler.class, handler);
    }

    @Test
    void kafkaListenerContainerFactory_setsManualImmediateAckMode() {
        KafkaConfig config = new KafkaConfig();
        ConsumerFactory<String, String> consumerFactory = mock(ConsumerFactory.class);
        CommonErrorHandler errorHandler = mock(CommonErrorHandler.class);

        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                config.kafkaListenerContainerFactory(consumerFactory, errorHandler);

        assertNotNull(factory);
        assertEquals(ContainerProperties.AckMode.MANUAL_IMMEDIATE, factory.getContainerProperties().getAckMode());
    }
}
