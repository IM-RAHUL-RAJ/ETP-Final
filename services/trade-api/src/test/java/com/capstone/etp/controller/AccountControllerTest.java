package com.capstone.etp.controller;

import com.capstone.etp.dto.AccountResponse;
import com.capstone.etp.dto.BalanceResponse;
import com.capstone.etp.dto.OrderHistoryEntry;
import com.capstone.etp.dto.PositionResponse;
import com.capstone.etp.enums.OrderStatus;
import com.capstone.etp.service.AccountService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AccountControllerTest {

    @Test
    void getAccount_returnsOk() {
        AccountService service = mock(AccountService.class);
        AccountController controller = new AccountController(service);
        AccountResponse expected = mock(AccountResponse.class);
        when(service.getAccount(1L)).thenReturn(expected);

        var response = controller.getAccount(1L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertSame(expected, response.getBody());
    }

    @Test
    void getBalance_returnsOk() {
        AccountService service = mock(AccountService.class);
        AccountController controller = new AccountController(service);
        BalanceResponse expected = mock(BalanceResponse.class);
        when(service.getBalance(1L)).thenReturn(expected);

        var response = controller.getBalance(1L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertSame(expected, response.getBody());
    }

    @Test
    void getPositions_returnsOk() {
        AccountService service = mock(AccountService.class);
        AccountController controller = new AccountController(service);
        List<PositionResponse> expected = List.of(mock(PositionResponse.class));
        when(service.getPositions(1L)).thenReturn(expected);

        var response = controller.getPositions(1L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertSame(expected, response.getBody());
    }

    @Test
    void getOrders_passesFiltersAndReturnsOk() {
        AccountService service = mock(AccountService.class);
        AccountController controller = new AccountController(service);
        Instant from = Instant.parse("2026-01-01T00:00:00Z");
        Instant to = Instant.parse("2026-01-31T23:59:59Z");
        List<OrderHistoryEntry> expected = List.of(mock(OrderHistoryEntry.class));
        when(service.getOrders(1L, OrderStatus.FILLED, from, to)).thenReturn(expected);

        var response = controller.getOrders(1L, OrderStatus.FILLED, from, to);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertSame(expected, response.getBody());
        verify(service).getOrders(1L, OrderStatus.FILLED, from, to);
    }
}
