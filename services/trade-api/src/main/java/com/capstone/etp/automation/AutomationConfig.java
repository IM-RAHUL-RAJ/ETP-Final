package com.capstone.etp.automation;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Wires automation. It is always present, but trades only for strategies a customer has switched on, and not at all
 * if app.automation.enabled is false. Orders are submitted on one dedicated thread so a slow Trade API cannot hold up
 * the market-data consumer; one thread also keeps the submissions in the order they triggered.
 */
@Configuration
@EnableConfigurationProperties(AutomationProperties.class)
class AutomationConfig {

    @Bean
    TradeApiClient automationTradeApiClient(AutomationProperties properties, AutomationTokenIssuer tokens) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(properties.getConnectTimeoutMs()));
        factory.setReadTimeout(Duration.ofMillis(properties.getReadTimeoutMs()));
        RestClient restClient = RestClient.builder()
                .baseUrl(properties.getTradeApiBaseUrl())
                .requestFactory(factory)
                .build();
        return new RestTradeApiClient(restClient, tokens);
    }

    @Bean(destroyMethod = "shutdown")
    ExecutorService automationOrderExecutor() {
        return Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "automation-orders");
            thread.setDaemon(true);
            return thread;
        });
    }

    @Bean
    AutomationService automationService(AutomationProperties properties, AutomationMapper store, HistorySeeder seeder,
                                        TradeApiClient tradeApi, ExecutorService automationOrderExecutor, Clock clock,
                                        AutomationEventMapper events) {
        AutomationService service = new AutomationService(properties, store, seeder, tradeApi, automationOrderExecutor, clock);
        service.useHistory(events);
        return service;
    }
}
