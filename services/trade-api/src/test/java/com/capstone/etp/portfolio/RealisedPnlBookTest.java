package com.capstone.etp.portfolio;

import com.capstone.etp.dto.BalanceResponse;
import com.capstone.etp.kafka.MalformedEventException;
import com.capstone.etp.mapper.AccountMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RealisedPnlBookTest {

    private static final Instant SOLD_AT = Instant.parse("2026-10-06T09:14:24Z");

    private InMemoryRealisedLedger ledger;
    private AccountMapper accountMapper;
    private RealisedPnlBook book;

    @BeforeEach
    void setUp() {
        ledger = new InMemoryRealisedLedger();
        accountMapper = mock(AccountMapper.class);
        book = new RealisedPnlBook(ledger, accountMapper);
        when(accountMapper.getBalance(1L)).thenReturn(BalanceResponse.builder().accountId(1L).currency("USD").build());
        when(accountMapper.getInstrumentCurrency("AAPL")).thenReturn("USD");
    }

    @Test
    void realisedProfitIsBookedAtTheSaleAsExecutedPriceMinusAverageCostTimesQuantity() {
        boolean booked = book.bookSale(UUID.randomUUID(), 1L, "AAPL", 10,
                new BigDecimal("120.00"), new BigDecimal("100.00"), SOLD_AT);

        assertTrue(booked);
        assertEquals(new BigDecimal("200.00"), ledger.rows.values().iterator().next().realisedPnl());
    }

    @Test
    void aSaleBelowCostBooksALoss() {
        book.bookSale(UUID.randomUUID(), 1L, "AAPL", 10, new BigDecimal("90.00"), new BigDecimal("100.00"), SOLD_AT);

        assertEquals(new BigDecimal("-100.00"), ledger.sumRealised(1L, null, null));
    }

    @Test
    void aReplayedEventIsNotCountedTwice() {
        UUID event = UUID.randomUUID();

        assertTrue(book.bookSale(event, 1L, "AAPL", 10, new BigDecimal("120.00"), new BigDecimal("100.00"), SOLD_AT));
        assertFalse(book.bookSale(event, 1L, "AAPL", 10, new BigDecimal("120.00"), new BigDecimal("100.00"), SOLD_AT));
        assertFalse(book.bookSale(event, 1L, "AAPL", 10, new BigDecimal("120.00"), new BigDecimal("100.00"), SOLD_AT));

        assertEquals(1, ledger.rows.size());
        assertEquals(new BigDecimal("200.00"), ledger.sumRealised(1L, null, null), "still 200, not 400 or 600");
    }

    @Test
    void twoDifferentSalesAreTwoBookings() {
        book.bookSale(UUID.randomUUID(), 1L, "AAPL", 10, new BigDecimal("120.00"), new BigDecimal("100.00"), SOLD_AT);
        book.bookSale(UUID.randomUUID(), 1L, "AAPL", 5, new BigDecimal("110.00"), new BigDecimal("100.00"), SOLD_AT);

        assertEquals(new BigDecimal("250.00"), ledger.sumRealised(1L, null, null));
    }

    @Test
    void theAverageCostInTheInstrumentsCurrencyIsConvertedToTheAccountsBeforeSubtracting() {
        // The executor reports executedPrice in the ACCOUNT currency and averageCostAfter in the INSTRUMENT currency.
        when(accountMapper.getInstrumentCurrency("INFY.NS")).thenReturn("INR");

        book.bookSale(UUID.randomUUID(), 1L, "INFY.NS", 40, new BigDecimal("17.50"), new BigDecimal("1580.25"), SOLD_AT);

        InMemoryRealisedLedger.Row row = ledger.rows.values().iterator().next();
        assertEquals(new BigDecimal("16.63"), row.averageCost(), "1580.25 INR at 95 INR per USD");
        assertEquals(new BigDecimal("34.80"), row.realisedPnl(), "(17.50 - 16.63) x 40");
        assertEquals("USD", row.baseCurrency());
    }

    @Test
    void theBookingDoesNotDependOnAnyCurrentPrice() {
        // There is no price lookup anywhere in the booking: it needs only the sale's own figures.
        book.bookSale(UUID.randomUUID(), 1L, "AAPL", 1, new BigDecimal("10.00"), new BigDecimal("4.00"), SOLD_AT);

        assertEquals(new BigDecimal("6.00"), ledger.sumRealised(1L, null, null));
    }

    @Test
    void anUnknownInstrumentOrAccountIsAPoisonMessage() {
        assertThrows(MalformedEventException.class, () -> book.bookSale(UUID.randomUUID(), 1L, "NOPE", 1,
                BigDecimal.ONE, BigDecimal.ONE, SOLD_AT));
        assertThrows(MalformedEventException.class, () -> book.bookSale(UUID.randomUUID(), 9L, "AAPL", 1,
                BigDecimal.ONE, BigDecimal.ONE, SOLD_AT));
        assertTrue(ledger.rows.isEmpty());
    }

    @Test
    void theWindowBoundsWhatIsSummed() {
        book.bookSale(UUID.randomUUID(), 1L, "AAPL", 1, new BigDecimal("10.00"), new BigDecimal("0.00"),
                Instant.parse("2026-09-15T12:00:00Z"));
        book.bookSale(UUID.randomUUID(), 1L, "AAPL", 1, new BigDecimal("20.00"), new BigDecimal("0.00"),
                Instant.parse("2026-10-05T12:00:00Z"));

        assertEquals(new BigDecimal("20.00"),
                ledger.sumRealised(1L, Instant.parse("2026-10-01T00:00:00Z"), Instant.parse("2026-10-20T00:00:00Z")));
        assertEquals(new BigDecimal("30.00"), ledger.sumRealised(1L, null, null));
    }
}
