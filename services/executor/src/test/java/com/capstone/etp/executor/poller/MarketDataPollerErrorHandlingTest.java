package com.capstone.etp.executor.poller;

import com.capstone.etp.executor.client.FauxnanceClient;
import com.capstone.etp.executor.exception.RateLimitException;
import com.capstone.etp.executor.exception.SymbolRequestException;
import com.capstone.etp.executor.exception.TransientNetworkException;
import com.capstone.etp.executor.kafka.models.QuotePayload;
import com.capstone.etp.executor.mapper.ExecutorOrderMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MarketDataPollerErrorHandlingTest {

    @Mock
    private ExecutorOrderMapper mapper;

    @Mock
    private FauxnanceClient fauxnanceClient;

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    private MarketDataPoller poller;

    @BeforeEach
    void setUp() {
        poller = new MarketDataPoller(mapper, fauxnanceClient, kafkaTemplate, new ObjectMapper().findAndRegisterModules());
        setField(poller, "marketDataTopic", "market-data");
        setField(poller, "pollIntervalSeconds", 60);
    }

    @Test
    void poll_stopsCycle_whenRateLimited() {
        List<String> symbols = List.of("AAPL", "MSFT");
        when(mapper.findSymbolsForMarketDataPoller()).thenReturn(symbols);
        when(fauxnanceClient.getBatchQuotes(symbols)).thenThrow(new RateLimitException(3));

        poller.poll();

        verify(kafkaTemplate, never()).send(eq("market-data"), anyString(), anyString());
    }

    @Test
    void poll_continues_whenBatchRejected() {
        List<String> symbols = List.of("AAPL", "MSFT");
        when(mapper.findSymbolsForMarketDataPoller()).thenReturn(symbols);
        when(fauxnanceClient.getBatchQuotes(symbols))
                .thenThrow(new SymbolRequestException("AAPL,MSFT", 400, "bad request"));

        poller.poll();

        verify(kafkaTemplate, never()).send(eq("market-data"), anyString(), anyString());
    }

    @Test
    void poll_continues_whenTransientFailureOccurs() {
        List<String> symbols = List.of("AAPL", "MSFT");
        when(mapper.findSymbolsForMarketDataPoller()).thenReturn(symbols);
        when(fauxnanceClient.getBatchQuotes(symbols))
                .thenThrow(new TransientNetworkException("AAPL,MSFT", 2, new RuntimeException("timeout")));

        poller.poll();

        verify(kafkaTemplate, never()).send(eq("market-data"), anyString(), anyString());
    }

    @Test
    void poll_skipsInvalidQuoteSymbols() {
        List<String> symbols = List.of("AAPL");
        QuotePayload missingSymbol = new QuotePayload();
        missingSymbol.setSymbol(" ");
        missingSymbol.setPrice(new BigDecimal("100"));

        when(mapper.findSymbolsForMarketDataPoller()).thenReturn(symbols);
        when(fauxnanceClient.getBatchQuotes(symbols)).thenReturn(List.of(missingSymbol));

        poller.poll();

        verify(kafkaTemplate, never()).send(eq("market-data"), anyString(), anyString());
    }

    @Test
    void poll_handlesSerializationErrorPerQuote() throws Exception {
        List<String> symbols = List.of("AAPL");
        QuotePayload quote = new QuotePayload();
        quote.setSymbol("AAPL");
        quote.setPrice(new BigDecimal("100"));

        ObjectMapper failingMapper = org.mockito.Mockito.spy(new ObjectMapper());
        doThrow(new JsonProcessingException("boom") {}).when(failingMapper).writeValueAsString(org.mockito.ArgumentMatchers.any());

        poller = new MarketDataPoller(mapper, fauxnanceClient, kafkaTemplate, failingMapper);
        setField(poller, "marketDataTopic", "market-data");
        setField(poller, "pollIntervalSeconds", 60);

        when(mapper.findSymbolsForMarketDataPoller()).thenReturn(symbols);
        when(fauxnanceClient.getBatchQuotes(symbols)).thenReturn(List.of(quote));

        poller.poll();

        verify(kafkaTemplate, times(0)).send(eq("market-data"), anyString(), anyString());
    }

    private static void setField(Object target, String fieldName, Object value) {
        try {
            var field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set field " + fieldName, e);
        }
    }
}
