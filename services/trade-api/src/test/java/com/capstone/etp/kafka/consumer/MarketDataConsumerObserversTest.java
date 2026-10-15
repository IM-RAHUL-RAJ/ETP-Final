package com.capstone.etp.kafka.consumer;

import com.capstone.etp.config.JacksonConfig;
import com.capstone.etp.dto.MarketQuote;
import com.capstone.etp.service.MarketQuoteCache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.annotation.KafkaListener;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarketDataConsumerObserversTest {

    private static final String QUOTE = """
            {"eventId":"3a5c7e91-2b4d-4f60-8c1e-9d0f2a4b6c8e","eventType":"QUOTE","eventTime":"2026-09-28T09:15:00Z",
             "source":"market-poller","schemaVersion":1,
             "payload":{"symbol":"AAPL","price":232.71,"bid":232.65,"ask":232.77,"currency":"USD","stale":false,
             "quoteAsOf":"2026-09-28T09:14:58Z"}}""";

    private MarketQuoteCache cache;
    private final List<MarketQuote> seen = new ArrayList<>();

    @BeforeEach
    void setUp() {
        cache = new MarketQuoteCache();
    }

    @Test
    void everyObserverSeesEveryQuoteAndTheCacheStillFills() {
        List<MarketQuote> second = new ArrayList<>();
        MarketDataConsumer consumer = new MarketDataConsumer(new JacksonConfig().objectMapper(), cache,
                List.of(seen::add, second::add));

        consumer.onMessage(QUOTE);

        assertEquals(1, seen.size());
        assertEquals(1, second.size());
        assertEquals("AAPL", seen.get(0).getSymbol());
        assertTrue(cache.get("AAPL").isPresent(), "the watchlist's live price keeps working");
    }

    @Test
    void anObserverThatFailsCannotStopTheOthersOrTheStream() {
        MarketDataConsumer consumer = new MarketDataConsumer(new JacksonConfig().objectMapper(), cache,
                List.of(quote -> {
                    throw new IllegalStateException("boom");
                }, seen::add));

        consumer.onMessage(QUOTE);

        assertEquals(1, seen.size());
        assertTrue(cache.get("AAPL").isPresent());
    }

    @Test
    void aMalformedMessageIsSkippedAndNoObserverIsCalled() {
        MarketDataConsumer consumer = new MarketDataConsumer(new JacksonConfig().objectMapper(), cache, List.of(seen::add));

        consumer.onMessage("{not json");
        consumer.onMessage("{\"eventType\":\"QUOTE\"}");

        assertTrue(seen.isEmpty());
    }

    @Test
    void theListenerUsesTheWatchlistGroupAndNoOtherListenerSharesIt() throws Exception {
        Method listener = MarketDataConsumer.class.getMethod("onMessage", String.class);
        KafkaListener annotation = listener.getAnnotation(KafkaListener.class);

        assertEquals("${app.kafka.groups.watchlist:watchlist-service}", annotation.groupId());
    }
}