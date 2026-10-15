package com.capstone.etp.portfolio;

import com.capstone.etp.exception.AccountAccessDeniedException;
import com.capstone.etp.exception.ModuleException;
import com.capstone.etp.security.AccountAuthorizer;
import com.capstone.etp.testsupport.TestIdentity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PortfolioHistoryServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-07T12:00:00Z");

    private PortfolioService portfolio;
    private PortfolioSnapshotMapper snapshots;
    private PortfolioHistoryService service;

    @BeforeEach
    void setUp() {
        portfolio = mock(PortfolioService.class);
        snapshots = mock(PortfolioSnapshotMapper.class);
        service = new PortfolioHistoryService(portfolio, snapshots, new AccountAuthorizer(), Clock.fixed(NOW, ZoneOffset.UTC));
        TestIdentity.customer(1L);
    }

    @AfterEach
    void tearDown() {
        TestIdentity.clear();
    }

    private static PortfolioSummaryResponse summary(String total) {
        BigDecimal value = new BigDecimal(total);
        return new PortfolioSummaryResponse(1L, "USD", value, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, null,
                BigDecimal.ZERO, value, 0, false, NOW);
    }

    private static PortfolioSnapshotMapper.Snapshot point(Instant at, String value) {
        PortfolioSnapshotMapper.Snapshot s = new PortfolioSnapshotMapper.Snapshot();
        s.setTakenAt(at);
        s.setTotalValue(new BigDecimal(value));
        return s;
    }

    @Test
    void aReadRecordsTheCurrentValueAndReturnsThePastPoints() {
        when(portfolio.summary(1L)).thenReturn(summary("1100.00"));
        when(snapshots.findSince(anyLong(), any())).thenReturn(List.of(point(NOW.minus(Duration.ofDays(2)), "1000.00")));

        PortfolioHistoryResponse response = service.history(1L, "30d");

        verify(snapshots).recordIfDue(1L, new BigDecimal("1100.00"), "USD");
        assertEquals(1, response.points().size());
        assertEquals("30d", response.range());
    }

    @Test
    void theDayChangeIsTheMoveSinceADayAgoMinusCashAddedOrTakenOut() {
        when(portfolio.summary(1L)).thenReturn(summary("1350.00"));
        when(snapshots.findAtOrBefore(1L, NOW.minus(PortfolioHistoryService.DAY)))
                .thenReturn(point(NOW.minus(Duration.ofHours(26)), "1000.00"));
        // 200 of the 350 rise was cash the customer added, so only 150 is performance.
        when(snapshots.netFundsSince(any(), any())).thenReturn(new BigDecimal("200.00"));

        PortfolioHistoryResponse.DayChange change = service.history(1L, "30d").dayChange();

        assertEquals(new BigDecimal("150.00"), change.amount());
        assertEquals(new BigDecimal("15.00"), change.percent());
    }

    @Test
    void whenTheHoldingsHaveAPreviousCloseTodayIsMeasuredFromIt() {
        PortfolioSummaryResponse base = summary("1350.00");
        when(portfolio.summary(1L)).thenReturn(new PortfolioSummaryResponse(1L, "USD", base.cashBalance(), BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, null, BigDecimal.ZERO, base.totalValue(), 1, false, NOW, new BigDecimal("42.00"), new BigDecimal("0.60")));

        PortfolioHistoryResponse.DayChange change = service.history(1L, "30d").dayChange();

        assertEquals(new BigDecimal("42.00"), change.amount());
        assertEquals(new BigDecimal("0.60"), change.percent());
        verify(snapshots, never()).findAtOrBefore(any(), any());
    }

    @Test
    void withoutAPointADayOldThereIsNoDayChangeRatherThanAGuess() {
        when(portfolio.summary(1L)).thenReturn(summary("1000.00"));
        when(snapshots.findAtOrBefore(any(), any())).thenReturn(null);

        assertNull(service.history(1L, "30d").dayChange());
    }

    @Test
    void whenPricesAreDownThePastIsStillShownAndNothingIsRecorded() {
        when(portfolio.summary(1L)).thenThrow(new PricingUnavailableException());
        when(snapshots.findSince(anyLong(), any())).thenReturn(List.of(point(NOW.minus(Duration.ofDays(1)), "900.00")));

        PortfolioHistoryResponse response = service.history(1L, "3m");

        assertEquals(1, response.points().size());
        assertNull(response.dayChange());
        verify(snapshots, never()).recordIfDue(any(), any(), any());
    }

    @Test
    void anUnknownRangeIsRefusedAndAnotherAccountIsForbidden() {
        assertThrows(ModuleException.class, () -> service.history(1L, "5y"));
        assertThrows(AccountAccessDeniedException.class, () -> service.history(2L, "30d"));
    }
}
