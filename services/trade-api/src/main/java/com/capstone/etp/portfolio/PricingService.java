package com.capstone.etp.portfolio;

import com.capstone.etp.dto.MarketQuote;
import com.capstone.etp.service.MarketQuoteCache;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

// Turns a set of symbols into prices, spending as little of the shared Fauxnance quota as it can.

@Slf4j
@Service
public class PricingService {

    public record PricedQuote(String symbol, BigDecimal price, String currency, Instant asOf, boolean stale, BigDecimal previousClose) {
        public PricedQuote(String symbol, BigDecimal price, String currency, Instant asOf, boolean stale) {
            this(symbol, price, currency, asOf, stale, null);
        }
    }

    private record CachedQuote(QuoteProvider.ProviderQuote quote, Instant fetchedAt) {
    }

    private final QuoteProvider provider;
    private final MarketQuoteCache topicCache;
    private final Clock clock;
    private final Duration ttl;
    private final Duration staleAfter;
    private final Duration cooldown;

    private final Map<String, CachedQuote> cache = new ConcurrentHashMap<>();
    private volatile Instant providerUnavailableUntil = Instant.MIN;

    PricingService(QuoteProvider provider,
                   MarketQuoteCache topicCache,
                   Clock clock,
                   @Value("${app.portfolio.quote-ttl-seconds:60}") long ttlSeconds,
                   @Value("${app.portfolio.stale-after-seconds:900}") long staleAfterSeconds,
                   @Value("${app.portfolio.provider-cooldown-seconds:15}") long cooldownSeconds) {
        this.provider = provider;
        this.topicCache = topicCache;
        this.clock = clock;
        this.ttl = Duration.ofSeconds(ttlSeconds);
        this.staleAfter = Duration.ofSeconds(staleAfterSeconds);
        this.cooldown = Duration.ofSeconds(cooldownSeconds);
    }

    public Map<String, PricedQuote> price(Collection<String> symbols) {
        Instant now = clock.instant();
        Map<String, PricedQuote> priced = new LinkedHashMap<>();
        List<String> missing = new ArrayList<>();

        for (String symbol : new LinkedHashSet<>(symbols.stream().map(PricingService::normalise).toList())) {
            CachedQuote cached = cache.get(symbol);
            if (cached != null && cached.fetchedAt().plus(ttl).isAfter(now)) {
                priced.put(symbol, toPriced(cached.quote(), now, false));
            } else {
                missing.add(symbol);
            }
        }

        if (!missing.isEmpty()) {
            fetchInto(priced, missing, now);
        }
        // "Today" needs yesterday's close. If the provider did not say, the market-data topic usually does.
        priced.replaceAll((symbol, quote) -> quote.previousClose() != null ? quote : withTopicClose(symbol, quote));
        return priced;
    }

    int cachedCount() {
        return cache.size();
    }

    private void fetchInto(Map<String, PricedQuote> priced, List<String> missing, Instant now) {
        if (!now.isBefore(providerUnavailableUntil)) {
            for (List<String> chunk : chunks(missing)) {
                try {
                    Map<String, QuoteProvider.ProviderQuote> fetched = provider.fetch(chunk);
                    fetched.forEach((symbol, quote) -> {
                        String key = normalise(symbol);
                        cache.put(key, new CachedQuote(quote, now));
                        priced.put(key, toPriced(quote, now, false));
                    });
                } catch (QuoteProvider.QuoteProviderException e) {
                    providerUnavailableUntil = now.plus(cooldown);
                    log.warn("[PORTFOLIO] quote provider unavailable, pausing calls for {}s: {}",
                            cooldown.toSeconds(), e.getMessage());
                    break;
                }
            }
        }

        for (String symbol : missing) {
            if (!priced.containsKey(symbol)) {
                fallback(symbol, now).ifPresent(quote -> priced.put(symbol, quote));
            }
        }
    }

    private Optional<PricedQuote> fallback(String symbol, Instant now) {
        CachedQuote expired = cache.get(symbol);
        Optional<MarketQuote> fromTopic = topicCache.get(symbol)
                .filter(quote -> quote.getPrice() != null && quote.getPrice().signum() > 0);

        Instant expiredAsOf = expired == null ? null : observedAt(expired.quote().asOf(), expired.fetchedAt());
        Instant topicAsOf = fromTopic.map(q -> observedAt(q.getQuoteAsOf(), q.getReceivedAt())).orElse(null);

        if (expired != null && (topicAsOf == null || !expiredAsOf.isBefore(topicAsOf))) {
            return Optional.of(toPriced(expired.quote(), now, true));
        }
        return fromTopic.map(quote -> new PricedQuote(
                symbol, quote.getPrice(), quote.getCurrency(), topicAsOf, true, quote.getPreviousClose()));
    }

    private PricedQuote withTopicClose(String symbol, PricedQuote quote) {
        BigDecimal close = topicCache.get(symbol).map(MarketQuote::getPreviousClose).filter(value -> value != null && value.signum() > 0).orElse(null);
        return close == null ? quote
                : new PricedQuote(quote.symbol(), quote.price(), quote.currency(), quote.asOf(), quote.stale(), close);
    }

    private PricedQuote toPriced(QuoteProvider.ProviderQuote quote, Instant now, boolean forceStale) {
        Instant asOf = quote.asOf();
        boolean old = asOf != null && Duration.between(asOf, now).compareTo(staleAfter) > 0;
        return new PricedQuote(normalise(quote.symbol()), quote.price(), quote.currency(), asOf,
                forceStale || quote.stale() || old, quote.previousClose());
    }

    private static Instant observedAt(Instant asOf, Instant fallback) {
        return asOf != null ? asOf : (fallback != null ? fallback : Instant.EPOCH);
    }

    private static List<List<String>> chunks(List<String> symbols) {
        List<List<String>> chunks = new ArrayList<>();
        for (int from = 0; from < symbols.size(); from += QuoteProvider.MAX_BATCH) {
            chunks.add(symbols.subList(from, Math.min(from + QuoteProvider.MAX_BATCH, symbols.size())));
        }
        return chunks;
    }

    private static String normalise(String symbol) {
        return symbol.trim().toUpperCase(Locale.ROOT);
    }
}
