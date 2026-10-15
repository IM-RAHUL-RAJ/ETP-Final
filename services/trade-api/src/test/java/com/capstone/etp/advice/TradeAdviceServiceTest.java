package com.capstone.etp.advice;

import com.capstone.etp.dto.PositionResponse;
import com.capstone.etp.dto.WatchlistEntry;
import com.capstone.etp.exception.AccountAccessDeniedException;
import com.capstone.etp.exception.ModuleException;
import com.capstone.etp.security.AccountAuthorizer;
import com.capstone.etp.service.AccountService;
import com.capstone.etp.testsupport.TestIdentity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TradeAdviceServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-07T12:00:00Z");

    private AccountService accounts;
    private PriceHistoryProvider history;
    private TradeAdviceService service;

    @BeforeEach
    void setUp() {
        TestIdentity.customer(1L);
        accounts = mock(AccountService.class);
        history = mock(PriceHistoryProvider.class);
        when(accounts.getPositions(1L)).thenReturn(List.of());
        when(accounts.getWatchlist(1L)).thenReturn(List.of());
        service = new TradeAdviceService(new AccountAuthorizer(), accounts, history,
                new MovingAverageAdviceStrategy(3, 6), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @AfterEach
    void tearDown() {
        TestIdentity.clear();
    }

    private static PositionResponse held(String symbol, int quantity) {
        return PositionResponse.builder().accountId(1L).symbol(symbol).quantity(quantity)
                .averageCost(BigDecimal.TEN).build();
    }

    private static WatchlistEntry watched(String symbol) {
        return WatchlistEntry.builder().symbol(symbol).build();
    }

    private static List<BigDecimal> series(double... values) {
        List<BigDecimal> list = new ArrayList<>();
        for (double value : values) {
            list.add(BigDecimal.valueOf(value));
        }
        return list;
    }

    @Test
    void adviceComesBackWithASignalReasoningAndATimestamp() {
        when(accounts.getPositions(1L)).thenReturn(List.of(held("AAPL", 10)));
        when(history.points("AAPL", 90)).thenReturn(PricePoint.undated(series(8, 9, 10, 14, 15, 16)));

        List<TradeAdvice> advice = service.adviceFor(null);

        assertEquals(1, advice.size());
        assertEquals("AAPL", advice.get(0).symbol());
        assertEquals(AdviceSignal.BUY, advice.get(0).signal());
        assertTrue(!advice.get(0).reasoning().isEmpty());
        assertEquals(NOW, advice.get(0).timestamp());
    }

    @Test
    void anEmptyPortfolioAndWatchlistGivesNoAdviceAndAsksForNoPrices() {
        assertTrue(service.adviceFor(null).isEmpty());
        verify(history, never()).points(anyString(), anyInt());
    }

    @Test
    void anEmptyPortfolioStillAdvisesOnTheWatchlist() {
        when(accounts.getWatchlist(1L)).thenReturn(List.of(watched("MSFT")));
        when(history.points("MSFT", 90)).thenReturn(PricePoint.undated(series(20, 19, 18, 12, 11, 10)));

        List<TradeAdvice> advice = service.adviceFor(null);

        assertEquals(1, advice.size());
        assertEquals(AdviceSignal.SELL, advice.get(0).signal());
    }

    @Test
    void anEmptyWatchlistStillAdvisesOnTheHoldings() {
        when(accounts.getPositions(1L)).thenReturn(List.of(held("AAPL", 3)));
        when(history.points("AAPL", 90)).thenReturn(PricePoint.undated(series(10, 10, 10, 10, 10, 10)));

        assertEquals(AdviceSignal.HOLD, service.adviceFor(null).get(0).signal());
    }

    @Test
    void anInstrumentHeldAndWatchedIsAnalysedOnce() {
        when(accounts.getPositions(1L)).thenReturn(List.of(held("AAPL", 3)));
        when(accounts.getWatchlist(1L)).thenReturn(List.of(watched("aapl"), watched("MSFT")));
        when(history.points(anyString(), anyInt())).thenReturn(PricePoint.undated(series(10, 10, 10, 10, 10, 10)));

        List<TradeAdvice> advice = service.adviceFor(null);

        assertEquals(List.of("AAPL", "MSFT"), advice.stream().map(TradeAdvice::symbol).toList());
        verify(history, times(1)).points("AAPL", 90);
    }

    @Test
    void aClosedPositionIsNotAnalysedAsAHolding() {
        when(accounts.getPositions(1L)).thenReturn(List.of(held("AAPL", 0)));

        assertTrue(service.adviceFor(null).isEmpty());
    }

    @Test
    void missingMarketDataGivesNoSignalRatherThanAGuess() {
        when(accounts.getPositions(1L)).thenReturn(List.of(held("AAPL", 10)));
        when(history.points("AAPL", 90)).thenReturn(PricePoint.undated(List.of()));

        TradeAdvice advice = service.adviceFor(null).get(0);

        assertEquals(AdviceSignal.INSUFFICIENT_DATA, advice.signal());
        assertTrue(advice.reasoning().contains("Available: 0."));
    }

    @Test
    void insufficientHistoryGivesNoBuySellOrHold() {
        when(accounts.getWatchlist(1L)).thenReturn(List.of(watched("AAPL")));
        when(history.points("AAPL", 90)).thenReturn(PricePoint.undated(series(1, 2, 3)));

        TradeAdvice advice = service.adviceFor(null).get(0);

        assertEquals(AdviceSignal.INSUFFICIENT_DATA, advice.signal());
        assertTrue(advice.reasoning().contains("Required: 6 observations."));
        assertTrue(advice.reasoning().contains("Available: 3."));
    }

    @Test
    void oneInstrumentCanBeAskedForByName() {
        when(accounts.getWatchlist(1L)).thenReturn(List.of(watched("MSFT")));
        when(history.points("MSFT", 90)).thenReturn(PricePoint.undated(series(8, 9, 10, 14, 15, 16)));

        assertEquals(AdviceSignal.BUY, service.adviceFor(null, " msft ").signal());
    }

    @Test
    void anInstrumentThatIsNeitherHeldNorWatchedIsNotAdvisedOn() {
        ModuleException refused = assertThrows(ModuleException.class, () -> service.adviceFor(null, "TSLA"));

        assertEquals("ADV-404", refused.getCatalogueCode());
        verify(history, never()).points(anyString(), anyInt());
    }

    @Test
    void anotherCustomersAccountIsRefusedAndNothingOfTheirsIsRead() {
        assertThrows(AccountAccessDeniedException.class, () -> service.adviceFor(2L));

        verify(accounts, never()).getPositions(2L);
        verify(accounts, never()).getWatchlist(2L);
    }

    @Test
    void aTokenWithNoAccountHasNothingToAskAbout() {
        TestIdentity.admin();

        assertThrows(AccountAccessDeniedException.class, () -> service.adviceFor(null));
    }
}
