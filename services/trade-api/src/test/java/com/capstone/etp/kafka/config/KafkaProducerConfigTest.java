package com.capstone.etp.kafka.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KafkaProducerConfigTest {

    @Test
    void kafkaTemplate_usesKafkaPropertiesProducerConfig() {
        KafkaProducerConfig config = new KafkaProducerConfig();
        KafkaProperties properties = mock(KafkaProperties.class);
        when(properties.buildProducerProperties()).thenReturn(Map.of());

        KafkaTemplate<String, String> template = config.kafkaTemplate(properties);

        assertNotNull(template);
        verify(properties).buildProducerProperties();
    }
}
