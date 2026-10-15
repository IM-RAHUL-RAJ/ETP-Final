package com.capstone.etp.automation;

import com.capstone.etp.dto.MarketQuote;
import com.capstone.etp.dto.PlaceOrderRequest;
import com.capstone.etp.security.JwtClaims;
import com.capstone.etp.security.JwtVerifier;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Switching on and off while it runs, the platform kill switch, and the token automation signs for itself. */
class AutomationReloadTest {

    private static final Instant T0 = Instant.parse("2026-10-07T10:00:00Z");
    private static final String SECRET = "test-secret-key-with-at-least-sixty-four-bytes-for-hs512-validation-12345";

    private final TradeApiClient tradeApi = mock(TradeApiClient.class);
    private final AutomationMapper store = mock(AutomationMapper.class);
    private final AutomationProperties properties = new AutomationProperties();
    private int tick;

    private static StrategyDefinition strategy() {
        StrategyDefinition definition = new StrategyDefinition();
        definition.setId("5");
        definition.setType(StrategyType.MA_CROSSOVER);
        definition.setAccountId(7L);
        definition.setSymbol("AAPL");
        definition.setQuantity(10);
        definition.setShortPeriod(2);
        definition.setLongPeriod(4);
        definition.setEnabled(true);
        return definition;
    }

    private AutomationService build() {
        when(tradeApi.submit(any())).thenReturn(TradeApiClient.SubmissionResult.submitted(UUID.randomUUID(), "NEW"));
        return new AutomationService(properties, store, tradeApi, Runnable::run, Clock.fixed(T0, ZoneOffset.UTC));
    }

    private void feed(AutomationService service, double... prices) {
        for (double price : prices) {
            service.onQuote(MarketQuote.builder().symbol("AAPL").price(BigDecimal.valueOf(price)).quoteAsOf(T0.plusSeconds(++tick)).build());
        }
    }

    @Test
    void aStrategySwitchedOnWhileRunningStartsTradingAndOneSwitchedOffStops() {
        when(store.findEnabled()).thenReturn(List.of());
        AutomationService service = build();
        feed(service, 10, 10, 10, 10, 12);
        verify(tradeApi, never()).submit(any());

        when(store.findEnabled()).thenReturn(List.of(strategy()));
        service.reload();
        feed(service, 12, 12, 12, 12, 14, 14, 14);
        verify(tradeApi, times(1)).submit(any());

        when(store.findEnabled()).thenReturn(List.of());
        service.reload();
        feed(service, 14, 14, 14, 14, 20);
        verify(tradeApi, times(1)).submit(any());
    }

    @Test
    void reportsHowManyPricesAreCollectedAgainstHowManyAreNeeded() {
        when(store.findEnabled()).thenReturn(List.of(strategy()));
        AutomationService service = build();

        feed(service, 10, 10, 10);

        assertEquals(new AutomationService.Progress(3, 5), service.progressOf("5").orElseThrow());
        assertTrue(service.progressOf("999").isEmpty());
    }

    @Test
    void thePlatformKillSwitchStopsEverythingEvenForSwitchedOnStrategies() {
        when(store.findEnabled()).thenReturn(List.of(strategy()));
        properties.setEnabled(false);
        AutomationService service = build();

        feed(service, 10, 10, 10, 10, 12);

        verify(tradeApi, never()).submit(any());
    }

    @Test
    void anUnreadableStoreKeepsTheStrategiesAlreadyRunning() {
        when(store.findEnabled()).thenReturn(List.of(strategy()));
        AutomationService service = build();
        when(store.findEnabled()).thenThrow(new IllegalStateException("db down"));

        service.reload();
        feed(service, 10, 10, 10, 10, 12);

        verify(tradeApi, times(1)).submit(any());
    }

    @Test
    void theTokenAutomationSignsIsAShortLivedCustomerTokenForOneAccountAndTheTradeApiAcceptsIt() {
        AutomationTokenIssuer issuer = new AutomationTokenIssuer(SECRET, "auth-service", Clock.systemUTC());

        JwtClaims claims = new JwtVerifier(SECRET, "HS256", "auth-service").verify(issuer.tokenFor(7L));

        assertEquals(List.of(7L), claims.accountIds());
        assertEquals(List.of("CUSTOMER"), claims.roles());
    }

    @Test
    void anExpiredAutomationTokenIsRefused() {
        AutomationTokenIssuer issuer = new AutomationTokenIssuer(SECRET, "auth-service",
                Clock.fixed(Instant.now().minusSeconds(3600), ZoneOffset.UTC));

        org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class,
                () -> new JwtVerifier(SECRET, "HS256", "auth-service").verify(issuer.tokenFor(7L)));
    }

    @Test
    void itPostsTheOrderWithATokenForTheOrdersOwnAccount() {
        // The wiring between the issuer and the client: the client asks for the order's account.
        TokenSource tokens = mock(TokenSource.class);
        when(tokens.tokenFor(7L)).thenReturn("t7");
        PlaceOrderRequest request = PlaceOrderRequest.builder().accountId(7L).build();

        assertEquals("t7", tokens.tokenFor(request.getAccountId()));
    }

    // ----------------------------------------------------------------------- the demo and the chart

    private static StrategyDefinition bands(int period) {
        StrategyDefinition definition = strategy();
        definition.setType(StrategyType.BOLLINGER_BANDS);
        definition.setPeriod(period);
        definition.setMultiplier(new BigDecimal("2"));
        return definition;
    }

    @Test
    void theDemoPathCrossesTheMovingAveragesUpAndDownAndPlacesARealOrderThroughTheTradeApi() {
        when(store.findEnabled()).thenReturn(List.of(strategy()));
        AutomationService service = build();

        var buy = service.simulate("5", SignalType.BUY, new BigDecimal("100"));
        var sell = service.simulate("5", SignalType.SELL, new BigDecimal("100"));

        assertEquals(SignalType.BUY, buy.trigger());
        assertEquals(new BigDecimal("103.00"), buy.finalPrice().setScale(2));
        assertEquals(new BigDecimal("97.00"), sell.finalPrice().setScale(2));
        var sent = org.mockito.ArgumentCaptor.forClass(PlaceOrderRequest.class);
        verify(tradeApi, times(2)).submit(sent.capture());
        assertEquals(com.capstone.etp.enums.OrderSide.BUY, sent.getAllValues().get(0).getSide());
        assertEquals(com.capstone.etp.enums.OrderSide.SELL, sent.getAllValues().get(1).getSide());
        assertEquals(7L, sent.getAllValues().get(0).getAccountId());
        assertEquals(new BigDecimal("103.00"), sent.getAllValues().get(0).getPrice());
        assertEquals(2, service.recentResults().stream().filter(r -> r.outcome() == OrderOutcome.ORDER_SUBMITTED).count());
        assertTrue(service.recentResults().stream().anyMatch(r -> r.detail() != null && r.detail().contains("demo price path")));
    }

    @Test
    void theDemoPathCrossesTheBollingerBandsBothWays() {
        when(store.findEnabled()).thenReturn(List.of(bands(10)));
        AutomationService service = build();

        assertEquals(SignalType.BUY, service.simulate("5", SignalType.BUY, new BigDecimal("50")).trigger());
        assertEquals(SignalType.SELL, service.simulate("5", SignalType.SELL, new BigDecimal("50")).trigger());
        verify(tradeApi, times(2)).submit(any());
    }

    @Test
    void theDemoNeverTouchesTheSharedPriceHistoryOrAnotherStrategyOnTheSameSymbol() {
        StrategyDefinition other = strategy();
        other.setId("6");
        other.setAccountId(8L);
        when(store.findEnabled()).thenReturn(List.of(strategy(), other));
        AutomationService service = build();

        service.simulate("5", SignalType.BUY, new BigDecimal("100"));

        // only the strategy that was asked for placed an order, and no price was added to the shared history
        var sent = org.mockito.ArgumentCaptor.forClass(PlaceOrderRequest.class);
        verify(tradeApi, times(1)).submit(sent.capture());
        assertEquals(7L, sent.getValue().getAccountId());
        assertEquals(new AutomationService.Progress(0, 5), service.progressOf("6").orElseThrow());
    }

    @Test
    void theDemoRefusesAStrategyThatIsNotRunning() {
        when(store.findEnabled()).thenReturn(List.of());
        AutomationService service = build();

        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, () -> service.simulate("5", SignalType.BUY, BigDecimal.TEN));
    }

    @Test
    void theChartHasThePricesCollectedAndTheLinesTheRuleComparesThemWith() {
        when(store.findEnabled()).thenReturn(List.of(strategy()));
        AutomationService service = build();
        feed(service, 10, 10, 10, 10, 12);

        var chart = service.chartOf("5").orElseThrow();

        assertEquals(5, chart.prices().size());
        assertEquals(List.of("shortAverage", "longAverage"), List.copyOf(chart.lines().keySet()));
        assertEquals(5, chart.lines().get("shortAverage").size());
        assertEquals(null, chart.lines().get("longAverage").get(2));
        assertEquals(new BigDecimal("10.50"), chart.lines().get("longAverage").get(4));
        assertEquals(new BigDecimal("11.00"), chart.lines().get("shortAverage").get(4));
        assertTrue(service.chartOf("999").isEmpty());
    }

    @Test
    void theBollingerChartHasThreeBands() {
        when(store.findEnabled()).thenReturn(List.of(bands(5)));
        AutomationService service = build();
        feed(service, 10, 11, 10, 11, 10, 11);

        var chart = service.chartOf("5").orElseThrow();

        assertEquals(List.of("upper", "middle", "lower"), List.copyOf(chart.lines().keySet()));
        assertTrue(chart.lines().get("upper").get(5).compareTo(chart.lines().get("lower").get(5)) > 0);
    }

    // ----------------------------------------------------------------------- starting on its own

    @Test
    void aStrategySwitchedOnStartsFromRecentPricesSoItCanActOnTheVeryNextLiveTick() {
        when(store.findEnabled()).thenReturn(List.of(strategy()));
        HistorySeeder seeder = (symbol, count) -> List.of(BigDecimal.TEN, BigDecimal.TEN, BigDecimal.TEN, BigDecimal.TEN);
        when(tradeApi.submit(any())).thenReturn(TradeApiClient.SubmissionResult.submitted(UUID.randomUUID(), "NEW"));
        AutomationService service = new AutomationService(properties, store, seeder, tradeApi, Runnable::run, Clock.fixed(T0, ZoneOffset.UTC));

        // already ready: four recent prices plus none live would be 4 of 5, so one live tick completes the window and crosses
        assertEquals(new AutomationService.Progress(4, 5), service.progressOf("5").orElseThrow());
        feed(service, 12);

        verify(tradeApi, times(1)).submit(any());
    }

    @Test
    void aSeederThatFindsNothingJustMeansTheStrategyWarmsUpFromTheLiveStream() {
        when(store.findEnabled()).thenReturn(List.of(strategy()));
        when(tradeApi.submit(any())).thenReturn(TradeApiClient.SubmissionResult.submitted(UUID.randomUUID(), "NEW"));
        AutomationService service = new AutomationService(properties, store, (symbol, count) -> List.of(), tradeApi, Runnable::run, Clock.fixed(T0, ZoneOffset.UTC));

        assertEquals(new AutomationService.Progress(0, 5), service.progressOf("5").orElseThrow());
        feed(service, 10, 10, 10, 10, 12);
        verify(tradeApi, times(1)).submit(any());
    }

    @Test
    void startingPricesNeverReplaceLivePricesAlreadyCollected() {
        when(store.findEnabled()).thenReturn(List.of(strategy()));
        AutomationService service = new AutomationService(properties, store, (symbol, count) -> List.of(), tradeApi, Runnable::run, Clock.fixed(T0, ZoneOffset.UTC));
        feed(service, 10, 10, 10);

        assertEquals(new AutomationService.Progress(3, 5), service.progressOf("5").orElseThrow());
    }
}
