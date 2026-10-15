package com.capstone.etp.automation;

import com.capstone.etp.dto.InstrumentResponse;
import com.capstone.etp.exception.AccountAccessDeniedException;
import com.capstone.etp.exception.ModuleException;
import com.capstone.etp.security.AccountAuthorizer;
import com.capstone.etp.service.AccountService;
import com.capstone.etp.testsupport.TestIdentity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AutomationControllerTest {

    private AutomationProperties properties;
    private AutomationMapper store;
    private AutomationService service;
    private AccountService accounts;
    private AutomationController controller;
    private com.capstone.etp.service.MarketQuoteCache quotes;
    private AutomationEventMapper events;

    @BeforeEach
    void setUp() {
        TestIdentity.customer(1L);
        properties = new AutomationProperties();
        store = mock(AutomationMapper.class);
        service = mock(AutomationService.class);
        accounts = mock(AccountService.class);
        InstrumentResponse apple = mock(InstrumentResponse.class);
        when(apple.getSymbol()).thenReturn("AAPL");
        when(accounts.getInstruments()).thenReturn(List.of(apple));
        when(service.progressOf(any())).thenReturn(Optional.empty());
        when(service.recentResults()).thenReturn(List.of());
        quotes = mock(com.capstone.etp.service.MarketQuoteCache.class);
        events = mock(AutomationEventMapper.class);
        controller = new AutomationController(properties, store, service, new AccountAuthorizer(), accounts, quotes, events);
    }

    @AfterEach
    void tearDown() {
        TestIdentity.clear();
    }

    private static StrategyDefinition stored(long id, long account, boolean enabled) {
        StrategyDefinition definition = new StrategyDefinition();
        definition.setId(String.valueOf(id));
        definition.setType(StrategyType.MA_CROSSOVER);
        definition.setAccountId(account);
        definition.setSymbol("AAPL");
        definition.setQuantity(10);
        definition.setShortPeriod(20);
        definition.setLongPeriod(50);
        definition.setEnabled(enabled);
        return definition;
    }

    private static AutomationController.CreateRequest request(long account, String symbol, int quantity) {
        return new AutomationController.CreateRequest(StrategyType.MA_CROSSOVER, account, symbol, quantity, 20, 50, null, null);
    }

    // ----------------------------------------------------------------------- create

    @Test
    void createsAStrategySwitchedOff() {
        when(store.insertWithinCap(eq(1L), eq("MA_CROSSOVER"), eq("AAPL"), eq(10), eq(20), eq(50), any(), any(), anyInt())).thenReturn(5L);
        when(store.findById(5L)).thenReturn(stored(5, 1, false));

        var response = controller.create(request(1, " aapl ", 10));

        assertEquals(201, response.getStatusCode().value());
        assertFalse(response.getBody().enabled());
        assertEquals("short 20, long 50", response.getBody().parameters());
    }

    @Test
    void creatingAStrategyPutsItsInstrumentOnTheWatchlistSoLivePricesFlow() {
        when(store.insertWithinCap(any(), any(), any(), any(), any(), any(), any(), any(), anyInt())).thenReturn(5L);
        when(store.findById(5L)).thenReturn(stored(5, 1, false));

        controller.create(request(1, "aapl", 10));

        verify(accounts).addToWatchlist(1L, "AAPL");
    }

    @Test
    void aWatchlistProblemDoesNotStopTheStrategyBeingCreated() {
        when(store.insertWithinCap(any(), any(), any(), any(), any(), any(), any(), any(), anyInt())).thenReturn(5L);
        when(store.findById(5L)).thenReturn(stored(5, 1, false));
        when(accounts.addToWatchlist(any(), any())).thenThrow(new IllegalStateException("db"));

        assertEquals(201, controller.create(request(1, "AAPL", 10)).getStatusCode().value());
    }

    @Test
    void refusesAnotherCustomersAccount() {
        assertThrows(AccountAccessDeniedException.class, () -> controller.create(request(2, "AAPL", 10)));

        verify(store, never()).insertWithinCap(any(), any(), any(), any(), any(), any(), any(), any(), anyInt());
    }

    @Test
    void refusesAnAdministrator() {
        TestIdentity.admin(1L);

        assertThrows(AccountAccessDeniedException.class, () -> controller.create(request(1, "AAPL", 10)));
    }

    @Test
    void refusesBadParametersAnUnknownInstrumentAndAFullAccount() {
        assertEquals("VAL-422", assertThrows(ModuleException.class, () -> controller.create(request(1, "AAPL", 0))).getCatalogueCode());
        assertEquals("VAL-422", assertThrows(ModuleException.class, () -> controller.create(request(1, "NOPE", 10))).getCatalogueCode());

        when(store.insertWithinCap(any(), any(), any(), any(), any(), any(), any(), any(), anyInt())).thenReturn(null);
        assertEquals("AUT-422", assertThrows(ModuleException.class, () -> controller.create(request(1, "AAPL", 10))).getCatalogueCode());
    }

    @Test
    void refusesPeriodsLongerThanTheHistoryKept() {
        properties.setHistorySize(30);

        assertThrows(ModuleException.class, () -> controller.create(request(1, "AAPL", 10)));
    }

    // ----------------------------------------------------------------------- switching on

    @Test
    void switchingOnNeedsAnExplicitAcknowledgement() {
        when(store.findById(5L)).thenReturn(stored(5, 1, false));

        ModuleException refused = assertThrows(ModuleException.class,
                () -> controller.setEnabled(5L, new AutomationController.EnabledRequest(true, null)));
        assertEquals("AUT-422", refused.getCatalogueCode());
        assertThrows(ModuleException.class, () -> controller.setEnabled(5L, new AutomationController.EnabledRequest(true, false)));

        verify(store, never()).setEnabled(any(), eq(true));
        verify(service, never()).reload();
    }

    @Test
    void switchingOnWithAcknowledgementSavesAndStartsTheStrategy() {
        when(store.findById(5L)).thenReturn(stored(5, 1, false), stored(5, 1, true));

        var response = controller.setEnabled(5L, new AutomationController.EnabledRequest(true, true));

        verify(store).setEnabled(5L, true);
        verify(service).reload();
        assertTrue(response.getBody().enabled());
    }

    @Test
    void switchingOffNeedsNoAcknowledgement() {
        when(store.findById(5L)).thenReturn(stored(5, 1, true), stored(5, 1, false));

        controller.setEnabled(5L, new AutomationController.EnabledRequest(false, null));

        verify(store).setEnabled(5L, false);
        verify(service).reload();
    }

    @Test
    void thePlatformKillSwitchBlocksSwitchingOn() {
        properties.setEnabled(false);
        when(store.findById(5L)).thenReturn(stored(5, 1, false));

        ModuleException refused = assertThrows(ModuleException.class,
                () -> controller.setEnabled(5L, new AutomationController.EnabledRequest(true, true)));

        assertEquals("AUT-409", refused.getCatalogueCode());
        verify(store, never()).setEnabled(any(), eq(true));
    }

    @Test
    void anInvalidStoredStrategyCannotBeSwitchedOn() {
        StrategyDefinition broken = stored(5, 1, false);
        broken.setQuantity(0);
        when(store.findById(5L)).thenReturn(broken);

        assertThrows(ModuleException.class, () -> controller.setEnabled(5L, new AutomationController.EnabledRequest(true, true)));
        verify(store, never()).setEnabled(any(), eq(true));
    }

    // ----------------------------------------------------------------------- someone else's strategy, delete, status

    @Test
    void anotherCustomersStrategyLooksLikeItDoesNotExist() {
        when(store.findById(9L)).thenReturn(stored(9, 2, true));

        assertEquals("AUT-404", assertThrows(ModuleException.class,
                () -> controller.setEnabled(9L, new AutomationController.EnabledRequest(false, null))).getCatalogueCode());
        assertEquals("AUT-404", assertThrows(ModuleException.class, () -> controller.remove(9L)).getCatalogueCode());
        verify(store, never()).delete(9L);
    }

    @Test
    void removesOwnStrategyAndStopsIt() {
        when(store.findById(5L)).thenReturn(stored(5, 1, true));

        assertEquals(204, controller.remove(5L).getStatusCode().value());

        verify(store).delete(5L);
        verify(service).reload();
    }

    @Test
    void statusShowsOnlyOwnStrategiesAndOwnResultsWithProgress() {
        when(store.findByAccount(1L)).thenReturn(List.of(stored(5, 1, true)));
        when(service.progressOf("5")).thenReturn(Optional.of(new AutomationService.Progress(12, 51)));
        AutomationEvent saved = new AutomationEvent();
        saved.setStrategyId(5L);
        saved.setAccountId(1L);
        saved.setStrategyType("MA_CROSSOVER");
        saved.setSymbol("AAPL");
        saved.setTriggerSide("BUY");
        saved.setOutcome("ORDER_SUBMITTED");
        saved.setCreatedAt(java.time.Instant.now());
        when(events.findByAccount(1L, 200)).thenReturn(List.of(saved));

        var body = controller.status().getBody();

        assertTrue(body.running());
        assertEquals(1, body.strategies().size());
        assertEquals(12, body.strategies().get(0).collected());
        assertEquals(51, body.strategies().get(0).required());
        assertEquals(1, body.results().size());
        verify(store, never()).findByAccount(2L);
        verify(events, never()).findByAccount(eq(2L), anyInt());
        assertEquals("MA_CROSSOVER", body.results().get(0).strategyType());
    }

    @Test
    void whenTheHistoryTableCannotBeReadRecentActivityIsShownInstead() {
        when(events.findByAccount(any(), anyInt())).thenThrow(new IllegalStateException("no table"));
        when(service.recentResults()).thenReturn(List.of(
                new AutomationResult("5", "AAPL", 1L, SignalType.BUY, OrderOutcome.ORDER_SUBMITTED, null, "ok", java.time.Instant.now())));

        assertEquals(1, controller.status().getBody().results().size());
    }

    @Test
    void aSwitchedOffStrategyHasNoProgress() {
        when(store.findByAccount(1L)).thenReturn(List.of(stored(5, 1, false)));

        assertNull(controller.status().getBody().strategies().get(0).collected());
    }

    @Test
    void multiplierIsPassedThroughForBollinger() {
        when(store.insertWithinCap(any(), eq("BOLLINGER_BANDS"), any(), any(), any(), any(), eq(20), eq(new BigDecimal("2")), anyInt())).thenReturn(6L);
        StrategyDefinition bands = stored(6, 1, false);
        bands.setType(StrategyType.BOLLINGER_BANDS);
        bands.setPeriod(20);
        bands.setMultiplier(new BigDecimal("2"));
        when(store.findById(6L)).thenReturn(bands);

        var response = controller.create(new AutomationController.CreateRequest(StrategyType.BOLLINGER_BANDS, 1L, "AAPL", 5, null, null, 20, new BigDecimal("2")));

        assertEquals("period 20, multiplier 2", response.getBody().parameters());
    }

    // ----------------------------------------------------------------------- the demo

    private static com.capstone.etp.dto.MarketQuote live(String price) {
        return com.capstone.etp.dto.MarketQuote.builder().symbol("AAPL").price(new BigDecimal(price)).build();
    }

    @Test
    void theDemoIsOffUnlessTheOperatorSwitchedItOn() {
        when(store.findById(5L)).thenReturn(stored(5, 1, true));

        ModuleException refused = assertThrows(ModuleException.class,
                () -> controller.simulate(5L, new AutomationController.SimulateRequest(SignalType.BUY)));

        assertEquals("AUT-403", refused.getCatalogueCode());
        verify(service, never()).simulate(any(), any(), any());
    }

    @Test
    void theDemoNeedsASwitchedOnStrategyAndALivePrice() {
        properties.setSimulationEnabled(true);
        when(store.findById(5L)).thenReturn(stored(5, 1, false));
        assertEquals("AUT-409", assertThrows(ModuleException.class,
                () -> controller.simulate(5L, new AutomationController.SimulateRequest(SignalType.BUY))).getCatalogueCode());

        when(store.findById(5L)).thenReturn(stored(5, 1, true));
        when(quotes.get("AAPL")).thenReturn(Optional.empty());
        assertEquals("AUT-409", assertThrows(ModuleException.class,
                () -> controller.simulate(5L, new AutomationController.SimulateRequest(SignalType.BUY))).getCatalogueCode());
        verify(service, never()).simulate(any(), any(), any());
    }

    @Test
    void theDemoRunsOnTheLatestLivePriceForTheOwnersStrategy() {
        properties.setSimulationEnabled(true);
        when(store.findById(5L)).thenReturn(stored(5, 1, true));
        when(quotes.get("AAPL")).thenReturn(Optional.of(live("200.00")));
        when(service.simulate("5", SignalType.BUY, new BigDecimal("200.00")))
                .thenReturn(new AutomationService.Simulation(52, new BigDecimal("200.00"), new BigDecimal("206.00"), SignalType.BUY));

        var response = controller.simulate(5L, new AutomationController.SimulateRequest(SignalType.BUY)).getBody();

        assertEquals(52, response.ticks());
        assertEquals("BUY", response.trigger());
    }

    @Test
    void theDemoCannotBeRunOnAnotherCustomersStrategyOrWithoutADirection() {
        properties.setSimulationEnabled(true);
        when(store.findById(9L)).thenReturn(stored(9, 2, true));
        assertEquals("AUT-404", assertThrows(ModuleException.class,
                () -> controller.simulate(9L, new AutomationController.SimulateRequest(SignalType.BUY))).getCatalogueCode());

        when(store.findById(5L)).thenReturn(stored(5, 1, true));
        when(quotes.get("AAPL")).thenReturn(Optional.of(live("200")));
        assertEquals("VAL-422", assertThrows(ModuleException.class,
                () -> controller.simulate(5L, new AutomationController.SimulateRequest(SignalType.NONE))).getCatalogueCode());
    }

    @Test
    void anAdministratorCannotRunTheDemo() {
        properties.setSimulationEnabled(true);
        TestIdentity.admin(1L);

        assertThrows(AccountAccessDeniedException.class,
                () -> controller.simulate(5L, new AutomationController.SimulateRequest(SignalType.BUY)));
    }
}
