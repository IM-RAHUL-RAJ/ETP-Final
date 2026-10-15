package com.capstone.etp.controller;

import com.capstone.etp.dto.OrderResponse;
import com.capstone.etp.dto.PlaceOrderRequest;
import com.capstone.etp.service.OrderService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrderControllerTest {

    @Test
    void placeOrder_returnsCreatedResponse() {
        OrderService orderService = mock(OrderService.class);
        OrderController controller = new OrderController(orderService);
        PlaceOrderRequest request = mock(PlaceOrderRequest.class);
        OrderResponse expected = mock(OrderResponse.class);
        when(orderService.placeOrder(request)).thenReturn(expected);

        ResponseEntity<OrderResponse> response = controller.placeOrder(request);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertSame(expected, response.getBody());
        verify(orderService).placeOrder(request);
    }

    @Test
    void cancelOrder_returnsOkResponse() {
        OrderService orderService = mock(OrderService.class);
        OrderController controller = new OrderController(orderService);
        UUID id = UUID.randomUUID();
        OrderResponse expected = mock(OrderResponse.class);
        when(orderService.cancelOrder(id)).thenReturn(expected);

        ResponseEntity<OrderResponse> response = controller.cancelOrder(id);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertSame(expected, response.getBody());
        verify(orderService).cancelOrder(id);
    }
}
