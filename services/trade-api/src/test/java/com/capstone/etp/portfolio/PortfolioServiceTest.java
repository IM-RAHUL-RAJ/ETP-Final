package com.capstone.etp.portfolio;

import com.capstone.etp.dto.BalanceResponse;
import com.capstone.etp.dto.InstrumentResponse;
import com.capstone.etp.dto.PositionResponse;
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
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PortfolioServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-19T14:05:11Z");

    private AccountService accounts;
    private PricingService pricing;
    private RealisedPnlMapper ledger;
    private PortfolioService service;

    @BeforeEach
    void setUp() {
        accounts = mock(AccountService.class);
        pricing = mock(PricingService.class);
        ledger = mock(RealisedPnlMapper.class);
        service = new PortfolioService(accounts, new AccountAuthorizer(), pricing, ledger,
                Clock.fixed(NOW, ZoneOffset.UTC));
        TestIdentity.customer(1L);

        when(accounts.getBalance(1L)).thenReturn(BalanceResponse.builder()
                .accountId(1L).cashBalance(new BigDecimal("24500.75")).currency("USD").build());
        when(accounts.getInstruments()).thenReturn(List.of(
                InstrumentResponse.builder().symbol("AAPL").currency("USD").build(),
                InstrumentResponse.builder().symbol("INFY.NS").currency("INR").build()));
        when(ledger.sumRealised(eq(1L), any(), any())).thenReturn(new BigDecimal("1145.50"));
    }

    @AfterEach
    void tearDown() {
        TestIdentity.clear();
    }

    private static PositionResponse position(String symbol, int quantity, String averageCost) {
        return PositionResponse.builder().accountId(1L).symbol(symbol).quantity(quantity)
                .averageCost(new BigDecimal(averageCost)).build();
    }

    private static PricingService.PricedQuote quote(String symbol, String price, String currency, boolean stale) {
        return new PricingService.PricedQuote(symbol, new BigDecimal(price), currency,
                Instant.parse("2026-10-19T14:04:58Z"), stale);
    }

    private void holdAaplAndInfy() {
        when(accounts.getPositions(1L)).thenReturn(List.of(position("AAPL", 200, "228.40"), position("INFY.NS", 40, "1580.25")));
    }

    // ---------------------------------------------------------------- today

    @Test
    void todayIsWhatTheHoldingsGainedSinceYesterdaysClose() {
        when(accounts.getPositions(1L)).thenReturn(List.of(position("AAPL", 200, "228.40")));
        when(pricing.price(anyList())).thenReturn(Map.of("AAPL", new PricingService.PricedQuote("AAPL", new BigDecimal("232.71"), "USD",
                Instant.parse("2026-10-19T14:04:58Z"), false, new BigDecimal("230.00"))));

        PortfolioSummaryResponse s = service.summary(1L);

        // 200 shares x (232.71 - 230.00)
        assertEquals(new BigDecimal("542.00"), s.dayChange());
        assertEquals(new BigDecimal("1.18"), s.dayChangePercent());
    }

    @Test
    void todayIsNullRatherThanZeroWhenNoHoldingHasAPreviousClose() {
        when(accounts.getPositions(1L)).thenReturn(List.of(position("AAPL", 200, "228.40")));
        when(pricing.price(anyList())).thenReturn(Map.of("AAPL", quote("AAPL", "232.71", "USD", false)));

        PortfolioSummaryResponse s = service.summary(1L);

        assertNull(s.dayChange());
        assertNull(s.dayChangePercent());
    }

    @Test
    void aFallingHoldingMakesTodayNegative() {
        when(accounts.getPositions(1L)).thenReturn(List.of(position("AAPL", 10, "228.40")));
        when(pricing.price(anyList())).thenReturn(Map.of("AAPL", new PricingService.PricedQuote("AAPL", new BigDecimal("90.00"), "USD",
                Instant.parse("2026-10-19T14:04:58Z"), false, new BigDecimal("100.00"))));

        PortfolioSummaryResponse s = service.summary(1L);

        assertEquals(new BigDecimal("-100.00"), s.dayChange());
        assertEquals(new BigDecimal("-10.00"), s.dayChangePercent());
    }

    // ---------------------------------------------------------------- the definitions

    @Test
    void costBasisMarketValueAndUnrealisedFollowTheContractDefinitions() {
        when(accounts.getPositions(1L)).thenReturn(List.of(position("AAPL", 200, "228.40")));
        when(pricing.price(anyList())).thenReturn(Map.of("AAPL", quote("AAPL", "232.71", "USD", false)));

        PricedPositionResponse p = service.positions(1L, null).get(0);

        assertEquals(new BigDecimal("45680.00"), p.costBasis(), "quantity x average cost");
        assertEquals(new BigDecimal("46542.00"), p.marketValue(), "quantity x last price");
        assertEquals(new BigDecimal("862.00"), p.unrealisedPnl(), "market value - cost basis");
        assertEquals(new BigDecimal("1.89"), p.unrealisedPnlPercent());
        assertEquals("USD", p.currency());
        assertEquals(Instant.parse("2026-10-19T14:04:58Z"), p.priceAsOf());
        assertFalse(p.stale());
    }

    @Test
    void totalValueIsCashPlusMarketValue() {
        when(accounts.getPositions(1L)).thenReturn(List.of(position("AAPL", 200, "228.40")));
        when(pricing.price(anyList())).thenReturn(Map.of("AAPL", quote("AAPL", "232.71", "USD", false)));

        PortfolioSummaryResponse s = service.summary(1L);

        assertEquals(new BigDecimal("24500.75"), s.cashBalance());
        assertEquals(new BigDecimal("46542.00"), s.marketValue());
        assertEquals(new BigDecimal("45680.00"), s.costBasis());
        assertEquals(new BigDecimal("862.00"), s.unrealisedPnl());
        assertEquals(new BigDecimal("71042.75"), s.totalValue());
        assertEquals("USD", s.baseCurrency());
        assertEquals(1, s.positionCount());
        assertFalse(s.partial());
        assertEquals(NOW, s.asOf());
    }

    @Test
    void realisedProfitComesFromTheLedgerNotFromTodaysPrice() {
        when(accounts.getPositions(1L)).thenReturn(List.of(position("AAPL", 200, "228.40")));
        when(pricing.price(anyList()))
                .thenReturn(Map.of("AAPL", quote("AAPL", "100.00", "USD", false)))
                .thenReturn(Map.of("AAPL", quote("AAPL", "999.00", "USD", false)));

        BigDecimal atLowPrice = service.summary(1L).realisedPnl();
        BigDecimal atHighPrice = service.summary(1L).realisedPnl();

        assertEquals(new BigDecimal("1145.50"), atLowPrice);
        assertEquals(atLowPrice, atHighPrice, "realised P&L has nothing to do with today's price");
    }

    // ---------------------------------------------------------------- currencies

    @Test
    void holdingsInAnotherCurrencyAreConvertedBeforeTheyAreSummed() {
        holdAaplAndInfy();
        when(pricing.price(anyList())).thenReturn(Map.of(
                "AAPL", quote("AAPL", "232.71", "USD", false),
                "INFY.NS", quote("INFY.NS", "1600.00", "INR", false)));

        PortfolioSummaryResponse s = service.summary(1L);

        // INFY: cost 63210.00 INR = 665.37 USD ; value 64000.00 INR = 673.68 USD (platform rate 95 INR per USD)
        assertEquals(new BigDecimal("47215.68"), s.marketValue());
        assertEquals(new BigDecimal("46345.37"), s.costBasis());
        assertEquals(new BigDecimal("870.31"), s.unrealisedPnl());
        assertFalse(s.partial());
        PricedPositionResponse infy = service.positions(1L, "INFY.NS").get(0);
        assertEquals("INR", infy.currency(), "a position keeps its own currency; only totals are converted");
        assertEquals(new BigDecimal("64000.00"), infy.marketValue());
    }

    @Test
    void aCurrencyThatCannotBeConvertedIsLeftOutOfTheTotalsAndFlaggedPartial() {
        when(accounts.getPositions(1L)).thenReturn(List.of(position("AAPL", 10, "100.00"), position("ODD", 10, "5.00")));
        when(accounts.getInstruments()).thenReturn(List.of(
                InstrumentResponse.builder().symbol("AAPL").currency("USD").build(),
                InstrumentResponse.builder().symbol("ODD").currency("XXX").build()));
        when(pricing.price(anyList())).thenReturn(Map.of(
                "AAPL", quote("AAPL", "110.00", "USD", false),
                "ODD", quote("ODD", "6.00", "XXX", false)));

        PortfolioSummaryResponse s = service.summary(1L);

        assertTrue(s.partial());
        assertEquals(new BigDecimal("1100.00"), s.marketValue(), "the unconvertible holding adds nothing it cannot express");
        assertEquals(2, s.positionCount(), "but it is still counted as held");
    }

    // ---------------------------------------------------------------- partial and unavailable

    @Test
    void whenSomeSymbolsCannotBePricedTheAnswerIsPartialAndMarked() {
        holdAaplAndInfy();
        when(pricing.price(anyList())).thenReturn(Map.of("AAPL", quote("AAPL", "232.71", "USD", false)));

        PortfolioSummaryResponse s = service.summary(1L);
        List<PricedPositionResponse> positions = service.positions(1L, null);

        assertTrue(s.partial());
        assertEquals(2, s.positionCount());
        assertEquals(new BigDecimal("46542.00"), s.marketValue(), "priced holdings only");
        PricedPositionResponse infy = positions.stream().filter(p -> p.symbol().equals("INFY.NS")).findFirst().orElseThrow();
        assertNull(infy.lastPrice());
        assertNull(infy.marketValue());
        assertNull(infy.unrealisedPnl());
        assertNull(infy.priceAsOf());
        assertTrue(infy.stale(), "an unpriced position is stale = true");
        assertEquals(new BigDecimal("63210.00"), infy.costBasis(), "cost basis is still known without a price");
    }

    @Test
    void whenNothingCanBePricedTheAnswerIsTheDocumentedServiceUnavailable() {
        holdAaplAndInfy();
        when(pricing.price(anyList())).thenReturn(Map.of());

        PricingUnavailableException refused = assertThrows(PricingUnavailableException.class, () -> service.summary(1L));

        assertEquals("MKT-503", refused.getCatalogueCode());
        assertEquals(503, refused.getStatus().value());
        assertThrows(PricingUnavailableException.class, () -> service.positions(1L, null));
        assertThrows(PricingUnavailableException.class, () -> service.pnl(1L, null, null, false));
    }

    @Test
    void anAccountWithNoHoldingsNeedsNoPricesAndIsNotAnOutage() {
        when(accounts.getPositions(1L)).thenReturn(List.of());

        PortfolioSummaryResponse s = service.summary(1L);

        assertEquals(new BigDecimal("24500.75"), s.totalValue());
        assertEquals(0, s.positionCount());
        assertFalse(s.partial());
        assertNull(s.unrealisedPnlPercent(), "no cost basis, nothing to measure against");
        verifyNoInteractions(pricing);
    }

    @Test
    void positionsWithNothingLeftAreExcluded() {
        when(accounts.getPositions(1L)).thenReturn(List.of(position("AAPL", 0, "170.00"), position("MSFT", 5, "300.00")));
        when(accounts.getInstruments()).thenReturn(List.of(
                InstrumentResponse.builder().symbol("MSFT").currency("USD").build()));
        when(pricing.price(anyList())).thenReturn(Map.of("MSFT", quote("MSFT", "310.00", "USD", false)));

        List<PricedPositionResponse> positions = service.positions(1L, null);

        assertEquals(List.of("MSFT"), positions.stream().map(PricedPositionResponse::symbol).toList());
    }

    // ---------------------------------------------------------------- P&L route

    @Test
    void pnlSumsRealisedAndUnrealised() {
        when(accounts.getPositions(1L)).thenReturn(List.of(position("AAPL", 200, "228.40")));
        when(pricing.price(anyList())).thenReturn(Map.of("AAPL", quote("AAPL", "232.71", "USD", false)));

        PnlResponse pnl = service.pnl(1L, null, null, false);

        assertEquals(new BigDecimal("1145.50"), pnl.realisedPnl());
        assertEquals(new BigDecimal("862.00"), pnl.unrealisedPnl());
        assertEquals(new BigDecimal("2007.50"), pnl.totalPnl());
        assertNull(pnl.bySymbol(), "only present when asked for");
        assertNull(pnl.from());
    }

    @Test
    void theDateRangeBoundsTheRealisedFigureOnly() {
        when(accounts.getPositions(1L)).thenReturn(List.of());
        LocalDate from = LocalDate.parse("2026-09-01");
        LocalDate to = LocalDate.parse("2026-10-19");

        PnlResponse pnl = service.pnl(1L, from, to, false);

        verify(ledger).sumRealised(1L, Instant.parse("2026-09-01T00:00:00Z"), Instant.parse("2026-10-20T00:00:00Z"));
        assertEquals(from, pnl.from());
        assertEquals(to, pnl.to());
    }

    @Test
    void aReversedDateRangeIsInvalidInput() {
        ModuleException refused = assertThrows(ModuleException.class,
                () -> service.pnl(1L, LocalDate.parse("2026-10-19"), LocalDate.parse("2026-09-01"), false));

        assertEquals("VAL-422", refused.getCatalogueCode());
    }

    @Test
    void theBySymbolBreakdownIncludesSymbolsSoldOutAndSymbolsStillHeld() {
        when(accounts.getPositions(1L)).thenReturn(List.of(position("AAPL", 200, "228.40")));
        when(pricing.price(anyList())).thenReturn(Map.of("AAPL", quote("AAPL", "232.71", "USD", false)));
        RealisedPnlMapper.SymbolRealised msft = new RealisedPnlMapper.SymbolRealised();
        msft.setSymbol("MSFT");
        msft.setRealised(new BigDecimal("300.00"));
        RealisedPnlMapper.SymbolRealised aapl = new RealisedPnlMapper.SymbolRealised();
        aapl.setSymbol("AAPL");
        aapl.setRealised(new BigDecimal("845.50"));
        when(ledger.sumRealisedBySymbol(eq(1L), any(), any())).thenReturn(List.of(aapl, msft));

        List<PnlResponse.SymbolPnl> breakdown = service.pnl(1L, null, null, true).bySymbol();

        assertEquals(List.of("AAPL", "MSFT"), breakdown.stream().map(PnlResponse.SymbolPnl::symbol).toList());
        assertEquals(new BigDecimal("1707.50"), breakdown.get(0).totalPnl(), "845.50 realised + 862.00 unrealised");
        assertEquals(new BigDecimal("0.00"), breakdown.get(1).unrealisedPnl(), "sold out: nothing unrealised");
        assertEquals(new BigDecimal("300.00"), breakdown.get(1).totalPnl());
    }

    // ---------------------------------------------------------------- access control

    @Test
    void anotherAccountsPortfolioIsRefusedBeforeAnythingIsRead() {
        assertThrows(AccountAccessDeniedException.class, () -> service.summary(2L));
        assertThrows(AccountAccessDeniedException.class, () -> service.positions(2L, null));
        assertThrows(AccountAccessDeniedException.class, () -> service.pnl(2L, null, null, true));

        verifyNoInteractions(pricing);
        verify(accounts, org.mockito.Mockito.never()).getBalance(anyLong());
    }

    @Test
    void anAdminTokenWhoseClaimIsAnotherAccountIsRefusedToo() {
        TestIdentity.admin(99L);

        assertThrows(AccountAccessDeniedException.class, () -> service.summary(1L));
    }
}
