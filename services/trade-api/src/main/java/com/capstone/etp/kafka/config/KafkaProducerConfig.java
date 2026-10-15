package com.capstone.etp.kafka.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

// Kafka producer configuration for ORDER_PLACED events.


@Configuration
@EnableKafka
public class KafkaProducerConfig {

    @Bean
    public KafkaTemplate<String, String> kafkaTemplate(KafkaProperties kafkaProperties) {
        ProducerFactory<String, String> producerFactory =
                new DefaultKafkaProducerFactory<>(kafkaProperties.buildProducerProperties());
        return new KafkaTemplate<>(producerFactory);
    }
}
