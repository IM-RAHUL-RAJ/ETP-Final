package com.capstone.etp.funds;

import com.capstone.etp.exception.AccountAccessDeniedException;
import com.capstone.etp.exception.ModuleException;
import com.capstone.etp.notifications.NotificationService;
import com.capstone.etp.security.AccountAuthorizer;
import com.capstone.etp.testsupport.TestIdentity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FundsServiceTest {

    private FundsMapper mapper;
    private NotificationService notifications;
    private FundsService service;

    @BeforeEach
    void setUp() {
        mapper = mock(FundsMapper.class);
        notifications = mock(NotificationService.class);
        service = new FundsService(mapper, new AccountAuthorizer(), notifications, new BigDecimal("1000000"));
        when(mapper.currencyOf(1L)).thenReturn("USD");
        TestIdentity.customer(1L);
    }

    @AfterEach
    void tearDown() {
        TestIdentity.clear();
    }

    private static FundsRequest request(String amount) {
        return new FundsRequest(new BigDecimal(amount), FundsMethod.UPI);
    }

    @Test
    void aDepositRaisesTheBalanceRecordsTheMovementAndTellsTheCustomer() {
        when(mapper.deposit(1L, new BigDecimal("250.00"))).thenReturn(new BigDecimal("1250.00"));

        FundsResponse response = service.deposit(1L, request("250.00"));

        assertEquals(FundsKind.DEPOSIT, response.kind());
        assertEquals(new BigDecimal("1250.00"), response.balanceAfter());
        assertEquals("USD", response.currency());
        verify(mapper).insertMovement(any(), eq1(), eqKind(FundsKind.DEPOSIT), any(), any(), any(), any());
        verify(notifications).recordFundsEvent(any(), anyLong(), anyString(), anyString());
    }

    @Test
    void aWithdrawalWithinTheBalanceSucceeds() {
        when(mapper.withdraw(1L, new BigDecimal("100.00"))).thenReturn(new BigDecimal("900.00"));

        FundsResponse response = service.withdraw(1L, request("100.00"));

        assertEquals(FundsKind.WITHDRAWAL, response.kind());
        assertEquals(new BigDecimal("900.00"), response.balanceAfter());
    }

    @Test
    void aWithdrawalLargerThanTheBalanceIsRefusedAndNothingIsRecorded() {
        when(mapper.withdraw(1L, new BigDecimal("5000.00"))).thenReturn(null);
        when(mapper.statusOf(1L)).thenReturn("ACTIVE");

        ModuleException refused = assertThrows(ModuleException.class, () -> service.withdraw(1L, request("5000.00")));

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, refused.getStatus());
        verify(mapper, never()).insertMovement(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void aSuspendedAccountCannotMoveFunds() {
        when(mapper.deposit(1L, new BigDecimal("10.00"))).thenReturn(null);
        when(mapper.withdraw(1L, new BigDecimal("10.00"))).thenReturn(null);
        when(mapper.statusOf(1L)).thenReturn("SUSPENDED");

        assertEquals(HttpStatus.FORBIDDEN, assertThrows(ModuleException.class, () -> service.deposit(1L, request("10.00"))).getStatus());
        assertEquals(HttpStatus.FORBIDDEN, assertThrows(ModuleException.class, () -> service.withdraw(1L, request("10.00"))).getStatus());
    }

    @Test
    void aSingleMovementOverTheLimitIsRefused() {
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY,
                assertThrows(ModuleException.class, () -> service.deposit(1L, request("1000000.01"))).getStatus());
        verify(mapper, never()).deposit(any(), any());
    }

    @Test
    void aFailedInboxMessageNeverUndoesTheMovement() {
        when(mapper.deposit(1L, new BigDecimal("10.00"))).thenReturn(new BigDecimal("110.00"));
        doThrow(new IllegalStateException("inbox down")).when(notifications).recordFundsEvent(any(), anyLong(), anyString(), anyString());

        assertEquals(new BigDecimal("110.00"), service.deposit(1L, request("10.00")).balanceAfter());
    }

    @Test
    void anotherAccountIsRefusedBeforeAnyMoneyMoves() {
        assertThrows(AccountAccessDeniedException.class, () -> service.deposit(2L, request("10.00")));
        assertThrows(AccountAccessDeniedException.class, () -> service.withdraw(2L, request("10.00")));
        assertThrows(AccountAccessDeniedException.class, () -> service.history(2L, null));
        verify(mapper, never()).deposit(any(), any());
        verify(mapper, never()).withdraw(any(), any());
    }

    private static Long eq1() {
        return org.mockito.ArgumentMatchers.eq(1L);
    }

    private static FundsKind eqKind(FundsKind kind) {
        return org.mockito.ArgumentMatchers.eq(kind);
    }
}
