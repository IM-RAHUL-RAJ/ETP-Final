package com.capstone.etp.kafka.consumer;

import com.capstone.etp.dto.MarketQuote;
import com.capstone.etp.service.MarketQuoteCache;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.List;


@Slf4j
@Component
public class MarketDataConsumer {

    private final ObjectMapper objectMapper;
    private final MarketQuoteCache cache;
    private final List<QuoteObserver> observers;

    @Autowired
    public MarketDataConsumer(ObjectMapper objectMapper, MarketQuoteCache cache, List<QuoteObserver> observers) {
        this.objectMapper = objectMapper;
        this.cache = cache;
        this.observers = observers;
    }

    public MarketDataConsumer(ObjectMapper objectMapper, MarketQuoteCache cache) {
        this(objectMapper, cache, List.of());
    }

    @KafkaListener(
            topics = "${app.kafka.topics.market-data:market-data}",
            groupId = "${app.kafka.groups.watchlist:watchlist-service}",
            autoStartup = "${app.market-data.consumer.enabled:true}")
    public void onMessage(String message) {
        try {
            JsonNode payload = objectMapper.readTree(message).get("payload");
            if (payload == null || payload.isNull()) {
                log.warn("market-data message without a payload, skipped");
                return;
            }
            MarketQuote quote = objectMapper.treeToValue(payload, MarketQuote.class);
            cache.put(quote);
            notifyObservers(quote);
        } catch (Exception e) {
            log.warn("Could not read a market-data message, skipped: {}", e.getMessage());
        }
    }

    /** Every observer sees every quote. One that fails is logged and cannot stop the others. */
    private void notifyObservers(MarketQuote quote) {
        for (QuoteObserver observer : observers) {
            try {
                observer.onQuote(quote);
            } catch (RuntimeException e) {
                log.error("Quote observer {} failed for {}: {}",
                        observer.getClass().getSimpleName(), quote.getSymbol(), e.getMessage());
            }
        }
    }
}