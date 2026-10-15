package com.capstone.etp.portfolio;

import com.capstone.etp.dto.MarketQuote;
import com.capstone.etp.service.MarketQuoteCache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PricingServiceTest {

    /** A clock the test moves by hand. */
    static class MutableClock extends Clock {
        Instant now;

        MutableClock(Instant start) {
            this.now = start;
        }

        void advance(Duration by) {
            now = now.plus(by);
        }

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    /** A provider that records every call and answers from a table. */
    static class FakeProvider implements QuoteProvider {
        final List<List<String>> calls = new ArrayList<>();
        final Map<String, ProviderQuote> table = new HashMap<>();
        boolean down;

        @Override
        public Map<String, ProviderQuote> fetch(List<String> symbols) throws QuoteProviderException {
            calls.add(List.copyOf(symbols));
            if (down) {
                throw new QuoteProviderException("provider down");
            }
            Map<String, ProviderQuote> answer = new HashMap<>();
            symbols.forEach(symbol -> {
                if (table.containsKey(symbol)) {
                    answer.put(symbol, table.get(symbol));
                }
            });
            return answer;
        }

        void put(String symbol, String price, Instant asOf, boolean stale) {
            table.put(symbol, new ProviderQuote(symbol, new BigDecimal(price), "USD", asOf, stale));
        }
    }

    private static final Instant T0 = Instant.parse("2026-10-06T10:00:00Z");

    private MutableClock clock;
    private FakeProvider provider;
    private MarketQuoteCache topicCache;
    private PricingService pricing;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(T0);
        provider = new FakeProvider();
        topicCache = new MarketQuoteCache();
        pricing = new PricingService(provider, topicCache, clock, 60, 900, 15);
    }

    // ---------------------------------------------------------------- positions are priced from a live quote

    @Test
    void positionsArePricedFromTheProvidersQuote() {
        provider.put("AAPL", "232.71", T0.minusSeconds(5), false);

        PricingService.PricedQuote quote = pricing.price(List.of("AAPL")).get("AAPL");

        assertEquals(new BigDecimal("232.71"), quote.price());
        assertEquals(T0.minusSeconds(5), quote.asOf(), "every priced figure carries when it was observed");
        assertFalse(quote.stale());
    }

    // ---------------------------------------------------------------- batching

    @Test
    void tenHoldingsCostOneCallNotTen() {
        List<String> symbols = IntStream.rangeClosed(1, 10).mapToObj(i -> "SYM" + i).toList();
        symbols.forEach(s -> provider.put(s, "10.00", T0, false));

        Map<String, PricingService.PricedQuote> priced = pricing.price(symbols);

        assertEquals(1, provider.calls.size(), "one batched call");
        assertEquals(10, provider.calls.get(0).size());
        assertEquals(10, priced.size());
    }

    @Test
    void moreThanTwentyFiveSymbolsAreSplitIntoBatchesOfTwentyFive() {
        List<String> symbols = IntStream.rangeClosed(1, 60).mapToObj(i -> "S" + i).toList();
        symbols.forEach(s -> provider.put(s, "1.00", T0, false));

        pricing.price(symbols);

        assertEquals(List.of(25, 25, 10), provider.calls.stream().map(List::size).toList());
    }

    @Test
    void duplicateSymbolsAreAskedForOnce() {
        provider.put("AAPL", "1.00", T0, false);

        pricing.price(List.of("AAPL", "aapl", " AAPL "));

        assertEquals(List.of(List.of("AAPL")), provider.calls);
    }

    // ---------------------------------------------------------------- cache

    @Test
    void aCachedQuoteIsReusedWithinTheCacheWindow() {
        provider.put("AAPL", "232.71", T0, false);

        pricing.price(List.of("AAPL"));
        clock.advance(Duration.ofSeconds(59));
        pricing.price(List.of("AAPL"));

        assertEquals(1, provider.calls.size(), "the second request is served from the cache");
    }

    @Test
    void aQuoteOlderThanTheCacheWindowIsFetchedAgain() {
        provider.put("AAPL", "232.71", T0, false);

        pricing.price(List.of("AAPL"));
        clock.advance(Duration.ofSeconds(61));
        provider.put("AAPL", "233.50", clock.instant(), false);
        PricingService.PricedQuote refreshed = pricing.price(List.of("AAPL")).get("AAPL");

        assertEquals(2, provider.calls.size());
        assertEquals(new BigDecimal("233.50"), refreshed.price());
    }

    @Test
    void onlyWhatIsMissingIsFetched() {
        provider.put("AAPL", "1.00", T0, false);
        provider.put("MSFT", "2.00", T0, false);
        pricing.price(List.of("AAPL"));

        pricing.price(List.of("AAPL", "MSFT"));

        assertEquals(List.of("MSFT"), provider.calls.get(1));
    }

    // ---------------------------------------------------------------- stale is a state, not an error

    @Test
    void aQuoteTheProviderFlaggedStaleIsServedAndMarked() {
        provider.put("AAPL", "232.71", T0.minusSeconds(30), true);

        PricingService.PricedQuote quote = pricing.price(List.of("AAPL")).get("AAPL");

        assertEquals(new BigDecimal("232.71"), quote.price(), "the number is served, not hidden");
        assertTrue(quote.stale());
    }

    @Test
    void aQuoteObservedLongAgoIsMarkedStaleEvenIfTheProviderDidNotSaySo() {
        provider.put("AAPL", "232.71", T0.minus(Duration.ofMinutes(20)), false);

        assertTrue(pricing.price(List.of("AAPL")).get("AAPL").stale());
    }

    // ---------------------------------------------------------------- provider failure

    @Test
    void whenTheProviderIsDownNothingIsPricedAndNothingIsInvented() {
        provider.down = true;

        assertTrue(pricing.price(List.of("AAPL", "MSFT")).isEmpty());
    }

    @Test
    void whenTheProviderFailsTheLastKnownPriceIsServedMarkedStale() {
        provider.put("AAPL", "232.71", T0, false);
        pricing.price(List.of("AAPL"));
        clock.advance(Duration.ofMinutes(2));
        provider.down = true;

        PricingService.PricedQuote quote = pricing.price(List.of("AAPL")).get("AAPL");

        assertEquals(new BigDecimal("232.71"), quote.price());
        assertTrue(quote.stale(), "a price served because the provider failed is never presented as live");
    }

    @Test
    void theMarketDataTopicIsTheFallbackWhenTheProviderIsDownAndNothingIsCached() {
        provider.down = true;
        topicCache.put(MarketQuote.builder().symbol("AAPL").price(new BigDecimal("231.00")).currency("USD")
                .quoteAsOf(T0.minusSeconds(90)).build());

        PricingService.PricedQuote quote = pricing.price(List.of("AAPL")).get("AAPL");

        assertEquals(new BigDecimal("231.00"), quote.price());
        assertEquals(T0.minusSeconds(90), quote.asOf());
        assertTrue(quote.stale());
    }

    @Test
    void aFailedProviderIsLeftAloneForTheCooldownSoQuotaAndLatencyAreNotWasted() {
        provider.down = true;
        pricing.price(List.of("AAPL"));
        clock.advance(Duration.ofSeconds(5));
        pricing.price(List.of("AAPL"));
        pricing.price(List.of("MSFT"));

        assertEquals(1, provider.calls.size(), "no further calls during the cooldown");

        clock.advance(Duration.ofSeconds(11));
        pricing.price(List.of("AAPL"));
        assertEquals(2, provider.calls.size(), "it is tried again once the cooldown is over");
    }

    @Test
    void aSymbolTheProviderDoesNotKnowIsSimplyUnpricedWhileTheOthersAre() {
        provider.put("AAPL", "232.71", T0, false);

        Map<String, PricingService.PricedQuote> priced = pricing.price(List.of("AAPL", "UNKNOWN"));

        assertEquals(1, priced.size());
        assertNull(priced.get("UNKNOWN"));
    }

    @Test
    void aPartialBatchFailureStillServesTheBatchesThatWorked() {
        List<String> symbols = IntStream.rangeClosed(1, 30).mapToObj(i -> "S" + i).toList();
        symbols.forEach(s -> provider.put(s, "1.00", T0, false));
        QuoteProvider flaky = new QuoteProvider() {
            int calls;

            @Override
            public Map<String, ProviderQuote> fetch(List<String> batch) throws QuoteProviderException {
                if (++calls == 2) {
                    throw new QuoteProviderException("second batch failed");
                }
                return provider.fetch(batch);
            }
        };
        PricingService partial = new PricingService(flaky, topicCache, clock, 60, 900, 15);

        Map<String, PricingService.PricedQuote> priced = partial.price(symbols);

        assertEquals(25, priced.size(), "the first batch of 25 was priced; the failed batch is left unpriced");
    }
}
