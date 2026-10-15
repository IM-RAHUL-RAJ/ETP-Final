package com.capstone.etp.service;

import com.capstone.etp.dto.MarketQuote;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The newest quote per symbol, fed by the market-data topic. Memory only: a restart starts empty
 * and fills as the executor's next poll arrives. An older quote never replaces a newer one.
 */
@Component
public class MarketQuoteCache {

    private final Map<String, MarketQuote> latest = new ConcurrentHashMap<>();

    public void put(MarketQuote quote) {
        if (quote == null || quote.getSymbol() == null || quote.getSymbol().isBlank()) {
            return;
        }
        quote.setReceivedAt(Instant.now());
        latest.merge(quote.getSymbol(), quote, (current, incoming) -> isOlder(incoming, current) ? current : incoming);
    }

    public Optional<MarketQuote> get(String symbol) {
        return Optional.ofNullable(latest.get(symbol));
    }

    private static boolean isOlder(MarketQuote incoming, MarketQuote current) {
        return incoming.getQuoteAsOf() != null
                && current.getQuoteAsOf() != null
                && incoming.getQuoteAsOf().isBefore(current.getQuoteAsOf());
    }
}
