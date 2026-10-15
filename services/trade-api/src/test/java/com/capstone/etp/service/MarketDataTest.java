package com.capstone.etp.service;

import com.capstone.etp.dto.AccountResponse;
import com.capstone.etp.dto.MarketQuote;
import com.capstone.etp.dto.WatchlistEntry;
import com.capstone.etp.kafka.consumer.MarketDataConsumer;
import com.capstone.etp.mapper.AccountMapper;
import com.capstone.etp.security.AccountAuthorizer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MarketDataTest {

    @Mock
    private AccountMapper accountMapper;

    @Mock
    private AccountAuthorizer accountAuthorizer;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    private static final String QUOTE_EVENT = """
            {"eventId":"2f1c3a52-6b1f-4d9a-9c0c-0d6c2d8b7a11","eventType":"QUOTE","eventTime":"2026-10-03T10:00:00Z",
             "source":"trade-executor","schemaVersion":1,
             "payload":{"symbol":"AAPL","price":193.12,"bid":193.10,"ask":193.14,"currency":"USD","change":1.2,
                        "changePercent":0.62,"previousClose":191.92,"marketState":"REGULAR","stale":false,
                        "quoteAsOf":"2026-10-03T10:00:00Z","somethingNew":"ignored"}}
            """;

    @Test
    void consumerStoresTheQuoteFromAnEnvelopeAndIgnoresUnknownFields() {
        MarketQuoteCache cache = new MarketQuoteCache();
        new MarketDataConsumer(objectMapper, cache).onMessage(QUOTE_EVENT);

        MarketQuote quote = cache.get("AAPL").orElseThrow();
        assertThat(quote.getBid()).isEqualByComparingTo("193.10");
        assertThat(quote.getAsk()).isEqualByComparingTo("193.14");
        assertThat(quote.getMarketState()).isEqualTo("REGULAR");
        assertThat(quote.getReceivedAt()).isNotNull();
    }

    @Test
    void aMalformedMessageIsSkippedWithoutThrowing() {
        MarketQuoteCache cache = new MarketQuoteCache();
        MarketDataConsumer consumer = new MarketDataConsumer(objectMapper, cache);

        consumer.onMessage("not json at all");
        consumer.onMessage("{\"eventType\":\"QUOTE\"}");

        assertThat(cache.get("AAPL")).isEmpty();
    }

    @Test
    void anOlderQuoteNeverReplacesANewerOne() {
        MarketQuoteCache cache = new MarketQuoteCache();
        cache.put(MarketQuote.builder().symbol("AAPL").bid(new BigDecimal("2")).quoteAsOf(Instant.parse("2026-10-03T10:05:00Z")).build());
        cache.put(MarketQuote.builder().symbol("AAPL").bid(new BigDecimal("1")).quoteAsOf(Instant.parse("2026-10-03T10:00:00Z")).build());

        assertThat(cache.get("AAPL").orElseThrow().getBid()).isEqualByComparingTo("2");
    }

    @Test
    void watchlistQuotesCoverOnlyWatchedSymbolsThatHaveBeenQuoted() {
        MarketQuoteCache cache = new MarketQuoteCache();
        cache.put(MarketQuote.builder().symbol("AAPL").build());
        cache.put(MarketQuote.builder().symbol("TSLA").build());
        when(accountMapper.getAccountById(1L)).thenReturn(AccountResponse.builder().id(1L).build());
        when(accountMapper.getWatchlist(1L)).thenReturn(List.of(
                WatchlistEntry.builder().symbol("AAPL").build(),
                WatchlistEntry.builder().symbol("MSFT").build()));

        List<MarketQuote> quotes = new MarketDataService(accountMapper, accountAuthorizer, cache).getWatchlistQuotes(1L);

        assertThat(quotes).extracting(MarketQuote::getSymbol).containsExactly("AAPL");
        verify(accountAuthorizer).ensureAccountAccess(1L);
    }
}
