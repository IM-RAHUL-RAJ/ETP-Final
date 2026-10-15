package com.capstone.etp.assistant;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Duration;

@Configuration
@EnableConfigurationProperties(AssistantProperties.class)
class AssistantConfig {

    @Bean
    GeminiClient geminiClient(AssistantProperties properties) {
        AssistantProperties.Gemini gemini = properties.getGemini();
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(gemini.getConnectTimeoutMs()));
        factory.setReadTimeout(Duration.ofMillis(gemini.getReadTimeoutMs()));
        RestClient restClient = RestClient.builder().baseUrl(gemini.getBaseUrl()).requestFactory(factory).build();
        return new RestGeminiClient(restClient, gemini.getApiKey(), gemini.getModel(), gemini.getFallbackModels(), gemini.getMaxOutputTokens());
    }

    @Bean
    ChatRateLimiter assistantRateLimiter(AssistantProperties properties, Clock clock) {
        return new ChatRateLimiter(properties.getRatePerMinute(), clock);
    }
}
