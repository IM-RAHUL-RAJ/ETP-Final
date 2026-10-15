package com.capstone.etp.automation;

import com.capstone.etp.dto.MarketQuote;
import com.capstone.etp.dto.PlaceOrderRequest;
import com.capstone.etp.enums.OrderSide;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** The strategy engine and the orchestrator, with the Trade REST API behind an interface and mocked. */
class AutomationServiceTest {

    private static final Instant T0 = Instant.parse("2026-10-07T10:00:00Z");

    private TradeApiClient tradeApi;
    private int tick;

    @BeforeEach
    void setUp() {
        tradeApi = mock(TradeApiClient.class);
        when(tradeApi.submit(any())).thenReturn(TradeApiClient.SubmissionResult.submitted(UUID.randomUUID(), "status NEW"));
        tick = 0;
    }

    private static StrategyDefinition ma(String id, String symbol, long account, boolean enabled) {
        StrategyDefinition definition = new StrategyDefinition();
        definition.setId(id);
        definition.setType(StrategyType.MA_CROSSOVER);
        definition.setAccountId(account);
        definition.setSymbol(symbol);
        definition.setQuantity(100);
        definition.setShortPeriod(2);
        definition.setLongPeriod(4);
        definition.setEnabled(enabled);
        return definition;
    }

    private AutomationService service(StrategyDefinition... definitions) {
        AutomationProperties properties = new AutomationProperties();
        AutomationMapper store = mock(AutomationMapper.class);
        // the store returns only strategies that are switched on, as the real query does
        when(store.findEnabled()).thenReturn(java.util.Arrays.stream(definitions).filter(StrategyDefinition::isEnabled).toList());
        // A direct executor: the order is submitted before onQuote returns, so the tests can look straight away.
        return new AutomationService(properties, store, tradeApi, Runnable::run, Clock.fixed(T0, ZoneOffset.UTC));
    }

    private MarketQuote quote(String symbol, double price) {
        return MarketQuote.builder().symbol(symbol).price(BigDecimal.valueOf(price)).currency("USD")
                .quoteAsOf(T0.plusSeconds(++tick)).build();
    }

    private void feed(AutomationService service, String symbol, double... prices) {
        for (double price : prices) {
            service.onQuote(quote(symbol, price));
        }
    }

    // ----------------------------------------------------------------------- an order through the Trade API

    @Test
    void aCrossoverPlacesAnOrderThroughTheTradeApiWithTheConfiguredAccountSymbolSideAndQuantity() {
        AutomationService service = service(ma("aapl-ma", "AAPL", 7, true));

        feed(service, "AAPL", 10, 10, 10, 10, 12);

        ArgumentCaptor<PlaceOrderRequest> sent = ArgumentCaptor.forClass(PlaceOrderRequest.class);
        verify(tradeApi, times(1)).submit(sent.capture());
        PlaceOrderRequest request = sent.getValue();
        assertEquals(7L, request.getAccountId());
        assertEquals("AAPL", request.getSymbol());
        assertEquals(OrderSide.BUY, request.getSide());
        assertEquals(100, request.getQuantity());
        // no ask in this quote, so the last price plus the 0.1% allowance
        assertEquals(new BigDecimal("12.01"), request.getPrice());
        assertTrue(request.getIdempotencyKey() != null);
    }

    @Test
    void aSellCrossoverPlacesASellOrder() {
        AutomationService service = service(ma("aapl-ma", "AAPL", 7, true));

        feed(service, "AAPL", 10, 10, 10, 10, 8);

        ArgumentCaptor<PlaceOrderRequest> sent = ArgumentCaptor.forClass(PlaceOrderRequest.class);
        verify(tradeApi).submit(sent.capture());
        assertEquals(OrderSide.SELL, sent.getValue().getSide());
    }

    @Test
    void theOutcomeIsRecordedAsTriggeredThenSubmittedWithTheOrderId() {
        UUID orderId = UUID.randomUUID();
        when(tradeApi.submit(any())).thenReturn(TradeApiClient.SubmissionResult.submitted(orderId, "status NEW"));
        AutomationService service = service(ma("aapl-ma", "AAPL", 7, true));

        feed(service, "AAPL", 10, 10, 10, 10, 12);

        List<AutomationResult> results = service.recentResults();
        assertEquals(2, results.size());
        assertEquals(OrderOutcome.ORDER_SUBMITTED, results.get(0).outcome());
        assertEquals(orderId, results.get(0).orderId());
        assertEquals(OrderOutcome.STRATEGY_TRIGGERED, results.get(1).outcome());
        assertEquals("aapl-ma", results.get(0).strategyId());
        assertEquals(SignalType.BUY, results.get(0).trigger());
    }

    // ----------------------------------------------------------------------- nothing to do

    @Test
    void noOrderWhileTheConditionIsNotMet() {
        AutomationService service = service(ma("aapl-ma", "AAPL", 7, true));

        feed(service, "AAPL", 10, 9, 8, 7, 6, 5);

        verifyNoInteractions(tradeApi);
        assertTrue(service.recentResults().isEmpty());
    }

    @Test
    void notEnoughHistoryMeansNoOrder() {
        AutomationService service = service(ma("aapl-ma", "AAPL", 7, true));

        feed(service, "AAPL", 10, 10, 12);

        verifyNoInteractions(tradeApi);
    }

    @Test
    void repeatedTicksAfterACrossoverDoNotPlaceTheOrderAgain() {
        AutomationService service = service(ma("aapl-ma", "AAPL", 7, true));

        feed(service, "AAPL", 10, 10, 10, 10, 12, 13, 14, 15, 16, 17);

        verify(tradeApi, times(1)).submit(any());
    }

    @Test
    void aQuoteDeliveredTwiceIsOneTick() {
        AutomationService service = service(ma("aapl-ma", "AAPL", 7, true));
        feed(service, "AAPL", 10, 10, 10, 10);
        MarketQuote crossing = quote("AAPL", 12);

        service.onQuote(crossing);
        service.onQuote(crossing);

        verify(tradeApi, times(1)).submit(any());
    }

    @Test
    void aSecondCrossoverLaterIsANewTriggerAndPlacesANewOrderWithAnotherKey() {
        AutomationService service = service(ma("aapl-ma", "AAPL", 7, true));

        feed(service, "AAPL", 10, 10, 10, 10, 12);
        feed(service, "AAPL", 12, 12, 12, 12, 12, 12, 12, 6);

        ArgumentCaptor<PlaceOrderRequest> sent = ArgumentCaptor.forClass(PlaceOrderRequest.class);
        verify(tradeApi, times(2)).submit(sent.capture());
        assertEquals(OrderSide.BUY, sent.getAllValues().get(0).getSide());
        assertEquals(OrderSide.SELL, sent.getAllValues().get(1).getSide());
        Set<UUID> keys = new HashSet<>();
        sent.getAllValues().forEach(request -> keys.add(request.getIdempotencyKey()));
        assertEquals(2, keys.size());
    }

    // ----------------------------------------------------------------------- safety

    @Test
    void aDisabledStrategyNeverPlacesAnOrder() {
        AutomationService service = service(ma("aapl-ma", "AAPL", 7, false));

        feed(service, "AAPL", 10, 10, 10, 10, 12);

        verifyNoInteractions(tradeApi);
    }

    @Test
    void aStrategyWithAnInvalidAccountNeverPlacesAnOrder() {
        AutomationService service = service(ma("bad-account", "AAPL", 0, true));

        feed(service, "AAPL", 10, 10, 10, 10, 12);

        verifyNoInteractions(tradeApi);
    }

    @Test
    void aStrategyWithAnInvalidSymbolNeverPlacesAnOrder() {
        AutomationService service = service(ma("bad-symbol", "BAD SYMBOL", 7, true));

        feed(service, "BAD SYMBOL", 10, 10, 10, 10, 12);
        feed(service, "BAD", 10, 10, 10, 10, 12);

        verifyNoInteractions(tradeApi);
    }

    @Test
    void aStrategyThatNeedsMoreHistoryThanIsKeptIsRefused() {
        AutomationProperties properties = new AutomationProperties();
        properties.setHistorySize(3);
        AutomationMapper store = mock(AutomationMapper.class);
        when(store.findEnabled()).thenReturn(List.of(ma("aapl-ma", "AAPL", 7, true)));
        AutomationService service = new AutomationService(properties, store, tradeApi, Runnable::run, Clock.fixed(T0, ZoneOffset.UTC));

        feed(service, "AAPL", 10, 10, 10, 10, 12);

        verifyNoInteractions(tradeApi);
    }

    @Test
    void malformedPriceEventsAreIgnored() {
        AutomationService service = service(ma("aapl-ma", "AAPL", 7, true));

        service.onQuote(null);
        service.onQuote(MarketQuote.builder().build());
        service.onQuote(MarketQuote.builder().symbol("AAPL").build());
        service.onQuote(MarketQuote.builder().symbol("AAPL").price(BigDecimal.ZERO).build());
        service.onQuote(MarketQuote.builder().symbol("AAPL").price(new BigDecimal("-5")).build());
        service.onQuote(MarketQuote.builder().symbol("  ").price(BigDecimal.TEN).build());

        verifyNoInteractions(tradeApi);
        // and the engine is still healthy afterwards
        feed(service, "AAPL", 10, 10, 10, 10, 12);
        verify(tradeApi, times(1)).submit(any());
    }

    @Test
    void symbolsAreProcessedIndependently() {
        AutomationService service = service(ma("aapl-ma", "AAPL", 7, true), ma("msft-ma", "MSFT", 8, true));

        // Interleaved ticks: AAPL crosses up, MSFT never crosses.
        for (int i = 0; i < 4; i++) {
            service.onQuote(quote("AAPL", 10));
            service.onQuote(quote("MSFT", 50 - i));
        }
        service.onQuote(quote("AAPL", 12));
        service.onQuote(quote("MSFT", 45));

        ArgumentCaptor<PlaceOrderRequest> sent = ArgumentCaptor.forClass(PlaceOrderRequest.class);
        verify(tradeApi, times(1)).submit(sent.capture());
        assertEquals("AAPL", sent.getValue().getSymbol());
        assertEquals(7L, sent.getValue().getAccountId());
    }

    @Test
    void aSymbolNobodyTradesIsNotEvenStored() {
        AutomationService service = service(ma("aapl-ma", "AAPL", 7, true));

        feed(service, "TSLA", 1, 2, 3, 4, 5, 6);

        verifyNoInteractions(tradeApi);
    }

    // ----------------------------------------------------------------------- the Trade API says no, or fails

    @Test
    void aRefusalIsRecordedAsRejectedNotAsSubmitted() {
        when(tradeApi.submit(any())).thenReturn(TradeApiClient.SubmissionResult.rejected("HTTP 422 VAL-422"));
        AutomationService service = service(ma("aapl-ma", "AAPL", 7, true));

        feed(service, "AAPL", 10, 10, 10, 10, 12);

        assertEquals(OrderOutcome.ORDER_REJECTED, service.recentResults().get(0).outcome());
        assertEquals(null, service.recentResults().get(0).orderId());
    }

    @Test
    void aFailureIsRecordedAsFailedAndIsNotRetried() {
        when(tradeApi.submit(any())).thenReturn(TradeApiClient.SubmissionResult.failed("HTTP 503"));
        AutomationService service = service(ma("aapl-ma", "AAPL", 7, true));

        feed(service, "AAPL", 10, 10, 10, 10, 12);

        assertEquals(OrderOutcome.ORDER_SUBMISSION_FAILED, service.recentResults().get(0).outcome());
        verify(tradeApi, times(1)).submit(any());
        // The next ticks must not "make up" the order either.
        feed(service, "AAPL", 13, 14);
        verify(tradeApi, times(1)).submit(any());
    }

    @Test
    void aTimeoutFromTheClientIsAFailureNotASuccessAndNeverEscapes() {
        when(tradeApi.submit(any())).thenThrow(new IllegalStateException("read timed out"));
        AutomationService service = service(ma("aapl-ma", "AAPL", 7, true));

        feed(service, "AAPL", 10, 10, 10, 10, 12);

        AutomationResult last = service.recentResults().get(0);
        assertEquals(OrderOutcome.ORDER_SUBMISSION_FAILED, last.outcome());
        verify(tradeApi, times(1)).submit(any());
    }

    @Test
    void twoStrategiesOnTheSameSymbolEachPlaceTheirOwnOrder() {
        AutomationService service = service(ma("first", "AAPL", 7, true), ma("second", "AAPL", 8, true));

        feed(service, "AAPL", 10, 10, 10, 10, 12);

        verify(tradeApi, times(2)).submit(any());
    }

    // ----------------------------------------------------------------------- the limit meets the executor's rule

    @Test
    void aBuyIsSentAtTheAskAndASellAtTheBidSoTheExecutorFillsThem() {
        AutomationService service = service(ma("aapl-ma", "AAPL", 7, true));
        MarketQuote ask = MarketQuote.builder().symbol("AAPL").price(new BigDecimal("100")).bid(new BigDecimal("99.90")).ask(new BigDecimal("100.10")).build();

        assertEquals(0, service.marketableLimit(SignalType.BUY, ask).compareTo(new BigDecimal("100.2001")));
        assertEquals(0, service.marketableLimit(SignalType.SELL, ask).compareTo(new BigDecimal("99.8001")));
        // BUY limit at or above the ask; SELL limit at or below the bid
        assertTrue(service.marketableLimit(SignalType.BUY, ask).compareTo(ask.getAsk()) >= 0);
        assertTrue(service.marketableLimit(SignalType.SELL, ask).compareTo(ask.getBid()) <= 0);
    }

    @Test
    void theTriggerIsSavedBeforeTheOrderGoesAndTheOutcomeAfter() {
        AutomationEventMapper events = mock(AutomationEventMapper.class);
        when(events.insertTrigger(any(), any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(41L);
        UUID orderId = UUID.randomUUID();
        when(tradeApi.submit(any())).thenReturn(TradeApiClient.SubmissionResult.submitted(orderId, "status NEW"));
        AutomationService service = service(ma("5", "AAPL", 7, true));
        service.useHistory(events);

        feed(service, "AAPL", 10, 10, 10, 10, 12);

        var inOrder = org.mockito.Mockito.inOrder(events, tradeApi);
        inOrder.verify(events).insertTrigger(org.mockito.ArgumentMatchers.eq(5L), org.mockito.ArgumentMatchers.eq(7L),
                org.mockito.ArgumentMatchers.eq("MA_CROSSOVER"), org.mockito.ArgumentMatchers.eq("AAPL"), org.mockito.ArgumentMatchers.eq("BUY"),
                any(), any(), org.mockito.ArgumentMatchers.eq(100), any());
        inOrder.verify(tradeApi).submit(any());
        inOrder.verify(events).recordOutcome(41L, "ORDER_SUBMITTED", orderId, null);
    }

    @Test
    void aHistoryThatCannotBeWrittenNeverStopsTheTrade() {
        AutomationEventMapper events = mock(AutomationEventMapper.class);
        when(events.insertTrigger(any(), any(), any(), any(), any(), any(), any(), any(), any())).thenThrow(new IllegalStateException("db"));
        AutomationService service = service(ma("5", "AAPL", 7, true));
        service.useHistory(events);

        feed(service, "AAPL", 10, 10, 10, 10, 12);

        verify(tradeApi, times(1)).submit(any());
    }
}
