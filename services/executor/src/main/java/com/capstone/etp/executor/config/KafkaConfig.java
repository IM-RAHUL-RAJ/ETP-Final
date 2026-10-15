package com.capstone.etp.executor.config;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;

import com.capstone.etp.executor.exception.DeadLetterException;
import com.capstone.etp.executor.kafka.consumer.DeadLetterPublisher;


@EnableKafka
@Configuration
public class KafkaConfig {

    @Bean
    public CommonErrorHandler kafkaErrorHandler(
            DeadLetterPublisher deadLetterPublisher,
            @Value("${app.kafka.retry.max-attempts:3}") int maxAttempts,
            @Value("${app.kafka.retry.initial-interval-ms:250}") long initialIntervalMs,
            @Value("${app.kafka.retry.multiplier:2.0}") double multiplier,
            @Value("${app.kafka.retry.max-interval-ms:2000}") long maxIntervalMs) {
        ExponentialBackOffWithMaxRetries backOff = new ExponentialBackOffWithMaxRetries(Math.max(0, maxAttempts - 1));
        backOff.setInitialInterval(initialIntervalMs);
        backOff.setMultiplier(multiplier);
        backOff.setMaxInterval(maxIntervalMs);

        DefaultErrorHandler errorHandler = new DefaultErrorHandler((record, exception) ->
                deadLetterPublisher.publish((ConsumerRecord<String, String>) record,
                        DeadLetterPublisher.RETRY_BUDGET_EXHAUSTED, exception), backOff);

        errorHandler.addNotRetryableExceptions(
                DeadLetterException.class);
        errorHandler.setAckAfterHandle(false);
        errorHandler.setCommitRecovered(true);
        return errorHandler;
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory(
            ConsumerFactory<String, String> consumerFactory,
            CommonErrorHandler kafkaErrorHandler) {
        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        factory.setCommonErrorHandler(kafkaErrorHandler);
        return factory;
    }
}
