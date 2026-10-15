package com.capstone.etp.alerts;

import com.capstone.etp.dto.MarketQuote;
import com.capstone.etp.exception.AccountAccessDeniedException;
import com.capstone.etp.exception.InstrumentNotFoundException;
import com.capstone.etp.exception.ModuleException;
import com.capstone.etp.mapper.AccountMapper;
import com.capstone.etp.notifications.NotificationStatusLookup;
import com.capstone.etp.security.AccountAuthorizer;
import com.capstone.etp.service.AccountService;
import com.capstone.etp.service.MarketQuoteCache;
import com.capstone.etp.testsupport.TestIdentity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PriceAlertServiceTest {

    private AlertMapper mapper;
    private AlertIndex index;
    private AccountMapper accountMapper;
    private MarketQuoteCache quotes;
    private NotificationStatusLookup statuses;
    private AlertService service;

    @BeforeEach
    void setUp() {
        mapper = mock(AlertMapper.class);
        index = mock(AlertIndex.class);
        accountMapper = mock(AccountMapper.class);
        quotes = mock(MarketQuoteCache.class);
        statuses = mock(NotificationStatusLookup.class);
        service = new AlertService(mapper, index, new AccountAuthorizer(), mock(AccountService.class),
                accountMapper, quotes, statuses, 25);
        when(accountMapper.countInstrumentsBySymbol("AAPL")).thenReturn(1);
        when(quotes.get(any())).thenReturn(Optional.empty());
        TestIdentity.customer(1L);
    }

    @AfterEach
    void tearDown() {
        TestIdentity.clear();
    }

    private static PriceAlert row(long id, AlertOperator operator, String threshold, boolean enabled, AlertStatus status) {
        PriceAlert alert = new PriceAlert();
        alert.setId(id);
        alert.setAccountId(1L);
        alert.setSymbol("AAPL");
        alert.setOperator(operator);
        alert.setDirection(operator.direction());
        alert.setThreshold(new BigDecimal(threshold));
        alert.setEnabled(enabled);
        alert.setStatus(status);
        alert.setDeliveryState(DeliveryState.NONE);
        alert.setCreatedAt(Instant.parse("2026-10-01T10:00:00Z"));
        return alert;
    }

    private static MarketQuote quote(String price) {
        return MarketQuote.builder().symbol("AAPL").price(new BigDecimal(price)).stale(false).build();
    }

    // ---------------------------------------------------------------- conditions are stored as given

    @Test
    void everyOperatorIsStoredWithItsOwnDirectionAndIndexed() {
        for (AlertOperator operator : AlertOperator.values()) {
            PriceAlert created = row(11L, operator, "250", true, AlertStatus.ACTIVE);
            when(mapper.insertWithinCap(eq(1L), eq("AAPL"), any(), eq(operator), any(), eq(true), eq(25))).thenReturn(11L);
            when(mapper.findByIdAndAccount(11L, 1L)).thenReturn(created);

            AlertResponse response = service.create(1L, new CreateAlertRequest("aapl", operator, new BigDecimal("250"), null));

            assertEquals(operator, response.condition());
            verify(mapper).insertWithinCap(1L, "AAPL", operator.direction(), operator, new BigDecimal("250"), true, 25);
        }
    }

    @Test
    void operatorsCompareTheWayTheirSymbolsSay() {
        BigDecimal level = new BigDecimal("100.00");
        assertTrue(AlertOperator.GT.matches(new BigDecimal("100.01"), level));
        assertFalse(AlertOperator.GT.matches(level, level));
        assertTrue(AlertOperator.GTE.matches(level, level));
        assertTrue(AlertOperator.LT.matches(new BigDecimal("99.99"), level));
        assertFalse(AlertOperator.LT.matches(level, level));
        assertTrue(AlertOperator.LTE.matches(level, level));
        assertTrue(AlertOperator.EQ.matches(new BigDecimal("100"), level), "equality ignores trailing zeros");
        assertFalse(AlertOperator.EQ.matches(new BigDecimal("100.01"), level));
    }

    @Test
    void anAlertAlreadyTrueIsRefusedButAnEqualityAlertIsNot() {
        when(quotes.get("AAPL")).thenReturn(Optional.of(quote("240")));

        ModuleException refused = assertThrows(ModuleException.class,
                () -> service.create(1L, new CreateAlertRequest("AAPL", AlertOperator.GTE, new BigDecimal("230"), null)));
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, refused.getStatus());

        when(mapper.insertWithinCap(any(), any(), any(), eq(AlertOperator.EQ), any(), anyBoolean(), anyInt())).thenReturn(12L);
        when(mapper.findByIdAndAccount(12L, 1L)).thenReturn(row(12L, AlertOperator.EQ, "240", true, AlertStatus.ACTIVE));
        assertEquals(AlertOperator.EQ,
                service.create(1L, new CreateAlertRequest("AAPL", AlertOperator.EQ, new BigDecimal("240"), null)).condition());
    }

    @Test
    void anUnknownSymbolIsRefused() {
        when(accountMapper.countInstrumentsBySymbol("NOPE")).thenReturn(0);

        assertThrows(InstrumentNotFoundException.class,
                () -> service.create(1L, new CreateAlertRequest("NOPE", AlertOperator.GT, BigDecimal.TEN, null)));
        verify(mapper, never()).insertWithinCap(any(), any(), any(), any(), any(), anyBoolean(), anyInt());
    }

    @Test
    void theCapIs409() {
        when(mapper.insertWithinCap(any(), any(), any(), any(), any(), anyBoolean(), anyInt())).thenReturn(null);

        ModuleException refused = assertThrows(ModuleException.class,
                () -> service.create(1L, new CreateAlertRequest("AAPL", AlertOperator.GT, BigDecimal.TEN, null)));
        assertEquals(HttpStatus.CONFLICT, refused.getStatus());
    }

    // ---------------------------------------------------------------- update and toggle

    @Test
    void anEditChangesTheConditionAndReindexes() {
        PriceAlert before = row(7L, AlertOperator.GTE, "230", true, AlertStatus.ACTIVE);
        PriceAlert after = row(7L, AlertOperator.LT, "200", true, AlertStatus.ACTIVE);
        when(mapper.findByIdAndAccount(7L, 1L)).thenReturn(before, after);
        when(mapper.updateCondition(7L, 1L, "AAPL", AlertDirection.BELOW, AlertOperator.LT, new BigDecimal("200"), true)).thenReturn(1);

        AlertResponse response = service.update(1L, 7L, new UpdateAlertRequest(null, AlertOperator.LT, new BigDecimal("200"), null));

        assertEquals(AlertOperator.LT, response.condition());
        verify(index).replace(before, after);
    }

    @Test
    void togglingChangesOnlyTheSwitchAndNeverTouchesHistory() {
        PriceAlert on = row(7L, AlertOperator.GTE, "230", true, AlertStatus.ACTIVE);
        PriceAlert off = row(7L, AlertOperator.GTE, "230", false, AlertStatus.ACTIVE);
        when(mapper.findByIdAndAccount(7L, 1L)).thenReturn(on, off);
        when(mapper.setEnabled(7L, 1L, false)).thenReturn(1);

        assertFalse(service.setEnabled(1L, 7L, false).enabled());

        verify(index).replace(on, off);
        verify(mapper, never()).delete(anyLong(), anyLong());
        verify(mapper, never()).insertTrigger(anyLong(), any(), any());
    }

    @Test
    void anAlertBeingHandedOverCannotBeChanged() {
        when(mapper.findByIdAndAccount(7L, 1L)).thenReturn(row(7L, AlertOperator.GTE, "230", true, AlertStatus.TRIGGERED));
        when(mapper.setEnabled(7L, 1L, true)).thenReturn(0);

        ModuleException refused = assertThrows(ModuleException.class, () -> service.setEnabled(1L, 7L, true));
        assertEquals(HttpStatus.CONFLICT, refused.getStatus());
    }

    // ---------------------------------------------------------------- history and delete

    @Test
    void triggerHistoryShowsEachFiringWithTheStateOfItsMessage() {
        when(mapper.findByIdAndAccount(7L, 1L)).thenReturn(row(7L, AlertOperator.GTE, "230", true, AlertStatus.ACTIVE));
        AlertTrigger sent = trigger(1L, "231.50", 456L);
        AlertTrigger unknown = trigger(2L, "232.00", null);
        when(mapper.findTriggers(7L, AlertService.TRIGGER_HISTORY_LIMIT)).thenReturn(List.of(sent, unknown));
        when(statuses.statusOf(List.of(456L))).thenReturn(Map.of(456L, "SENT"));

        AlertTriggersResponse history = service.triggers(1L, 7L);

        assertEquals(2, history.triggers().size());
        assertEquals("SENT", history.triggers().get(0).notificationStatus());
        assertEquals("UNKNOWN", history.triggers().get(1).notificationStatus());
        assertEquals(new BigDecimal("231.50"), history.triggers().get(0).triggerPrice());
    }

    private static AlertTrigger trigger(long id, String price, Long notificationId) {
        AlertTrigger trigger = new AlertTrigger();
        trigger.setId(id);
        trigger.setAlertId(7L);
        trigger.setEventId(UUID.randomUUID());
        trigger.setTriggerPrice(new BigDecimal(price));
        trigger.setTriggeredAt(Instant.parse("2026-10-07T14:05:11Z"));
        trigger.setNotificationId(notificationId);
        return trigger;
    }

    @Test
    void deleteRemovesTheAlertAndLeavesTheIndex() {
        when(mapper.findByIdAndAccount(7L, 1L)).thenReturn(row(7L, AlertOperator.GTE, "230", true, AlertStatus.ACTIVE));
        when(mapper.delete(7L, 1L)).thenReturn(1);

        service.remove(1L, 7L);

        verify(mapper).delete(7L, 1L);
        verify(index).remove("AAPL", 7L);
    }

    @Test
    void deleteIsRefusedWhileTheFiredAlertIsStillBeingHandedOver() {
        when(mapper.findByIdAndAccount(7L, 1L)).thenReturn(row(7L, AlertOperator.GTE, "230", true, AlertStatus.TRIGGERED));
        when(mapper.delete(7L, 1L)).thenReturn(0);

        assertEquals(HttpStatus.CONFLICT, assertThrows(ModuleException.class, () -> service.remove(1L, 7L)).getStatus());
    }

    // ---------------------------------------------------------------- isolation

    @Test
    void anotherAccountIsRefusedBeforeAnythingIsRead() {
        assertThrows(AccountAccessDeniedException.class, () -> service.list(2L));
        assertThrows(AccountAccessDeniedException.class, () -> service.triggers(2L, 7L));
        assertThrows(AccountAccessDeniedException.class, () -> service.setEnabled(2L, 7L, true));
        assertThrows(AccountAccessDeniedException.class, () -> service.remove(2L, 7L));
        verify(mapper, never()).findByIdAndAccount(anyLong(), anyLong());
    }

    @Test
    void anAlertOfAnotherAccountLooksLikeItDoesNotExist() {
        when(mapper.findByIdAndAccount(7L, 1L)).thenReturn(null);

        ModuleException missing = assertThrows(ModuleException.class, () -> service.triggers(1L, 7L));
        assertEquals(HttpStatus.NOT_FOUND, missing.getStatus());
    }
}
