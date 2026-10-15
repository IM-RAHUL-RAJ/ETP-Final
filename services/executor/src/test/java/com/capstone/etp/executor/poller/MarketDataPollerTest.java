package com.capstone.etp.executor.poller;

import com.capstone.etp.executor.client.FauxnanceClient;
import com.capstone.etp.executor.kafka.models.QuotePayload;
import com.capstone.etp.executor.mapper.ExecutorOrderMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MarketDataPollerTest {

    @Mock
    private ExecutorOrderMapper mapper;

    @Mock
    private FauxnanceClient fauxnanceClient;

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    private MarketDataPoller poller;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();
        poller = new MarketDataPoller(mapper, fauxnanceClient, kafkaTemplate, objectMapper);
        setField(poller, "marketDataTopic", "market-data");
        setField(poller, "pollIntervalSeconds", 60);
    }

    @Test
    void batchUpTo25Symbols_fetchesInSingleRequest() {
        List<String> symbols = new ArrayList<>();
        for (int i = 1; i <= 25; i++) {
            symbols.add("SYM" + i);
        }

        when(mapper.findSymbolsForMarketDataPoller()).thenReturn(symbols);
        when(fauxnanceClient.getBatchQuotes(symbols)).thenReturn(List.of());

        poller.poll();

        verify(fauxnanceClient, times(1)).getBatchQuotes(symbols);
    }

    @Test
    void eachQuote_isPublishedAsOwnKafkaMessage_keyedBySymbol() {
        List<String> symbols = List.of("AAPL", "INFY.NS");
        when(mapper.findSymbolsForMarketDataPoller()).thenReturn(symbols);
        when(fauxnanceClient.getBatchQuotes(symbols)).thenReturn(List.of(
                quote("AAPL", "331.34"),
                quote("INFY.NS", "1060.00")
        ));

        poller.poll();

        verify(kafkaTemplate).send(eq("market-data"), eq("AAPL"), anyString());
        verify(kafkaTemplate).send(eq("market-data"), eq("INFY.NS"), anyString());
        verify(kafkaTemplate, times(2)).send(eq("market-data"), anyString(), anyString());
    }

    @Test
    void quotaArithmetic_intervalWithinDailyQuota_forDeclaredSymbolSet() {
        int requiredInterval = MarketDataPoller.requiredIntervalSeconds(8);

        assertThat(requiredInterval).isEqualTo(45);
        assertThat(MarketDataPoller.isIntervalWithinDailyQuota(45, 8)).isTrue();
        assertThat(MarketDataPoller.requestsPerDay(8, 45)).isEqualTo(1920);
    }

    @Test
    void intervalFloorEnforced_pollSkipped_whenConfiguredIntervalTooLow() {
        List<String> symbols = List.of("AAPL", "INFY.NS", "EURUSD", "BTCUSD", "RELIANCE.NS", "MSFT", "GOOGL", "TSLA");
        when(mapper.findSymbolsForMarketDataPoller()).thenReturn(symbols);
        setField(poller, "pollIntervalSeconds", 30);

        poller.poll();

        verify(fauxnanceClient, never()).getBatchQuotes(symbols);
        verify(kafkaTemplate, never()).send(eq("market-data"), anyString(), anyString());
    }

    private static QuotePayload quote(String symbol, String price) {
        QuotePayload payload = new QuotePayload();
        payload.setSymbol(symbol);
        payload.setPrice(new BigDecimal(price));
        payload.setBid(new BigDecimal(price));
        payload.setAsk(new BigDecimal(price));
        payload.setCurrency("USD");
        return payload;
    }

    private static void setField(Object target, String fieldName, Object value) {
        try {
            var field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set field: " + fieldName, e);
        }
    }
}
