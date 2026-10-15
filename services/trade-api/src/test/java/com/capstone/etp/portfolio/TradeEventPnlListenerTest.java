package com.capstone.etp.portfolio;

import com.capstone.etp.config.JacksonConfig;
import com.capstone.etp.dto.BalanceResponse;
import com.capstone.etp.kafka.MalformedEventException;
import com.capstone.etp.mapper.AccountMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TradeEventPnlListenerTest {

    private static final UUID SALE = UUID.fromString("d47f9a10-3e2b-4c88-b0a1-7e6d5c4b3a29");

    private InMemoryRealisedLedger ledger;
    private TradeEventPnlListener listener;

    @BeforeEach
    void setUp() {
        ledger = new InMemoryRealisedLedger();
        AccountMapper accountMapper = mock(AccountMapper.class);
        when(accountMapper.getBalance(1L)).thenReturn(BalanceResponse.builder().accountId(1L).currency("USD").build());
        when(accountMapper.getInstrumentCurrency("AAPL")).thenReturn("USD");
        listener = new TradeEventPnlListener(new JacksonConfig().objectMapper(), new RealisedPnlBook(ledger, accountMapper));
    }

    private static String event(UUID id, String type, String side, String extra) {
        return """
                {"eventId":"%s","eventType":"%s","eventTime":"2026-10-06T09:14:24Z","source":"trade-executor","schemaVersion":1,
                 "payload":{"orderId":"6f2b1c2a-6a1e-4a4f-9c0d-2f7a1b3c4d5e","accountId":1,"symbol":"AAPL","side":"%s",
                 "quantity":10,"price":115.00,"executedPrice":120.00,"status":"FILLED","reason":null,"cashDelta":1200.00,
                 "positionQuantityAfter":5,"averageCostAfter":100.00,"executedOn":"2026-10-06T09:14:24Z"%s}}"""
                .formatted(id, type, side, extra);
    }

    @Test
    void aFilledSellBooksRealisedProfit() {
        listener.handle(event(SALE, "ORDER_FILLED", "SELL", ""));

        assertEquals(new BigDecimal("200.00"), ledger.sumRealised(1L, null, null));
    }

    @Test
    void replayingTheEventDoesNotDoubleCountRealisedProfit() {
        listener.handle(event(SALE, "ORDER_FILLED", "SELL", ""));
        listener.handle(event(SALE, "ORDER_FILLED", "SELL", ""));
        listener.handle(event(SALE, "ORDER_FILLED", "SELL", ""));

        assertEquals(1, ledger.rows.size());
        assertEquals(new BigDecimal("200.00"), ledger.sumRealised(1L, null, null));
    }

    @Test
    void aBuyRealisesNothing() {
        listener.handle(event(SALE, "ORDER_FILLED", "BUY", ""));

        assertTrue(ledger.rows.isEmpty());
    }

    @Test
    void aRejectedSellRealisesNothing() {
        listener.handle(event(SALE, "ORDER_REJECTED", "SELL", ""));
        listener.handle(event(UUID.randomUUID(), "ORDER_CANCELLED", "SELL", ""));

        assertTrue(ledger.rows.isEmpty());
    }

    @Test
    void theSaleIsBookedAtWhenItHappened() {
        listener.handle(event(SALE, "ORDER_FILLED", "SELL", ""));

        assertEquals("2026-10-06T09:14:24Z", ledger.rows.get(SALE).bookedAt().toString());
    }

    @Test
    void aSaleWithoutAnExecutedPriceIsMalformed() {
        assertThrows(MalformedEventException.class, () -> listener.handle(
                event(SALE, "ORDER_FILLED", "SELL", "").replace("\"executedPrice\":120.00", "\"executedPrice\":null")));
    }

    @Test
    void notJsonIsMalformed() {
        assertThrows(MalformedEventException.class, () -> listener.handle("nonsense"));
    }

    @Test
    void anInvalidEventIdIsMalformed() {
        assertThrows(MalformedEventException.class, () -> listener.handle(
                event(SALE, "ORDER_FILLED", "SELL", "").replace(SALE.toString(), "nope")));
    }
}
