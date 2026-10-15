package com.capstone.etp.service;

import com.capstone.etp.dto.OrderResponse;
import com.capstone.etp.dto.PlaceOrderRequest;
import com.capstone.etp.entity.Account;
import com.capstone.etp.entity.Instrument;
import com.capstone.etp.entity.Order;
import com.capstone.etp.entity.PortfolioHolding;
import com.capstone.etp.enums.OrderSide;
import com.capstone.etp.enums.OrderStatus;
import com.capstone.etp.exception.AccountNotFoundException;
import com.capstone.etp.exception.InstrumentNotFoundException;
import com.capstone.etp.exception.OptimisticLockException;
import com.capstone.etp.kafka.producer.OrderEventProducer;
import com.capstone.etp.mapper.OrderMapper;
import com.capstone.etp.security.AccountAuthorizer;
import com.capstone.etp.validator.OrderValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderMapper orderMapper;

    @Mock
    private OrderValidator orderValidator;

    @Mock
    private AccountAuthorizer accountAuthorizer;

    @Mock
    private OrderEventProducer orderEventProducer;

    @InjectMocks
    private OrderService orderService;

    private Account account;
    private Instrument instrument;
    private PortfolioHolding holding;

    @BeforeEach
    void setUp() {
        account = mock(Account.class);
        instrument = mock(Instrument.class);
        holding = mock(PortfolioHolding.class);
        lenient().when(account.getId()).thenReturn(1L);
        lenient().when(account.getVersion()).thenReturn(7L);
        lenient().when(account.getCurrency()).thenReturn("USD");
        lenient().when(instrument.getCurrency()).thenReturn("USD");
    }

    @Test
    void placeOrder_shouldCreateBuyOrderSuccessfully() {

        UUID idempotencyKey =
                UUID.fromString("b8a987a1-7bdd-460f-8b70-b42e2b7ae65c");

        PlaceOrderRequest request = mock(PlaceOrderRequest.class);

        when(request.getAccountId()).thenReturn(1L);
        when(request.getSymbol()).thenReturn("AAPL");
        when(request.getIdempotencyKey()).thenReturn(idempotencyKey);
        when(request.getSide()).thenReturn(OrderSide.BUY);
        when(request.getQuantity()).thenReturn(5);
        when(request.getPrice()).thenReturn(new BigDecimal("150.00"));

        when(instrument.getId()).thenReturn(100L);
        when(instrument.getSymbol()).thenReturn("AAPL");

        when(orderMapper.findAccountById(1L))
                .thenReturn(account);

        when(orderMapper.findInstrumentBySymbol("AAPL"))
                .thenReturn(instrument);

        when(orderMapper.findPortfolioHolding(1L, 100L))
                .thenReturn(holding);

        when(orderMapper.findExistingOrdersByAccount(1L))
                .thenReturn(Collections.emptyList());
        when(orderMapper.incrementAccountVersionIfVersionMatches(1L, 7L))
                .thenReturn(1);

        OrderResponse response =
                orderService.placeOrder(request);

        assertNotNull(response);
        assertEquals(OrderStatus.NEW, response.getStatus());
        assertEquals("AAPL", response.getSymbol());
        assertEquals(OrderSide.BUY, response.getSide());
        assertEquals(5, response.getQuantity());
        assertEquals(
                new BigDecimal("150.00"),
                response.getPrice()
        );
        assertEquals(
                "Order created successfully",
                response.getMessage()
        );

        verify(orderValidator, times(1))
                .validate(
                        any(Order.class),
                        eq(account),
                        eq(instrument),
                        eq(holding),
                        anyList()
                );

        verify(orderMapper, times(1))
                .insertOrder(any(Order.class));
        verify(orderMapper).incrementAccountVersionIfVersionMatches(1L, 7L);
        verify(accountAuthorizer)
                .ensureAccountAccess(1L);
    }

    @Test
    void placeOrder_shouldThrowAccountNotFound_whenAccountDoesNotExist() {

        PlaceOrderRequest request = mock(PlaceOrderRequest.class);

        when(request.getAccountId()).thenReturn(999L);

        when(orderMapper.findAccountById(999L))
                .thenReturn(null);

        assertThrows(
                AccountNotFoundException.class,
                () -> orderService.placeOrder(request)
        );

        verify(orderMapper, never())
                .findInstrumentBySymbol(anyString());

        verify(orderMapper, never())
                .insertOrder(any(Order.class));

        verify(orderValidator, never())
                .validate(any(), any(), any(), any(), any());
        verify(accountAuthorizer)
                .ensureAccountAccess(999L);
    }

    @Test
    void placeOrder_shouldThrowInstrumentNotFound_whenInstrumentDoesNotExist() {

        PlaceOrderRequest request = mock(PlaceOrderRequest.class);

        when(request.getAccountId()).thenReturn(1L);
        when(request.getSymbol()).thenReturn("UNKNOWN");

        when(orderMapper.findAccountById(1L))
                .thenReturn(account);

        when(orderMapper.findInstrumentBySymbol("UNKNOWN"))
                .thenReturn(null);

        assertThrows(
                InstrumentNotFoundException.class,
                () -> orderService.placeOrder(request)
        );

        verify(orderMapper, never())
                .findPortfolioHolding(anyLong(), anyLong());

        verify(orderMapper, never())
                .insertOrder(any(Order.class));

        verify(orderValidator, never())
                .validate(any(), any(), any(), any(), any());
        verify(accountAuthorizer)
                .ensureAccountAccess(1L);
    }

    @Test
    void placeOrder_shouldCreateSellOrderSuccessfully() {

        UUID idempotencyKey =
                UUID.fromString("c9b098b2-8cee-4710-9c81-c53f3c8bf76d");

        PlaceOrderRequest request = mock(PlaceOrderRequest.class);

        when(request.getAccountId()).thenReturn(1L);
        when(request.getSymbol()).thenReturn("AAPL");
        when(request.getIdempotencyKey()).thenReturn(idempotencyKey);
        when(request.getSide()).thenReturn(OrderSide.SELL);
        when(request.getQuantity()).thenReturn(5);
        when(request.getPrice()).thenReturn(new BigDecimal("160.00"));

        when(instrument.getId()).thenReturn(100L);
        when(instrument.getSymbol()).thenReturn("AAPL");

        when(orderMapper.findAccountById(1L))
                .thenReturn(account);

        when(orderMapper.findInstrumentBySymbol("AAPL"))
                .thenReturn(instrument);

        when(orderMapper.findPortfolioHolding(1L, 100L))
                .thenReturn(holding);

        when(orderMapper.findExistingOrdersByAccount(1L))
                .thenReturn(Collections.emptyList());
        when(orderMapper.incrementAccountVersionIfVersionMatches(1L, 7L))
                .thenReturn(1);

        OrderResponse response =
                orderService.placeOrder(request);

        assertNotNull(response);
        assertEquals(OrderStatus.NEW, response.getStatus());
        assertEquals("AAPL", response.getSymbol());
        assertEquals(OrderSide.SELL, response.getSide());
        assertEquals(5, response.getQuantity());
        assertEquals(
                new BigDecimal("160.00"),
                response.getPrice()
        );

        verify(orderValidator, times(1))
                .validate(
                        any(Order.class),
                        eq(account),
                        eq(instrument),
                        eq(holding),
                        anyList()
                );

        verify(orderMapper, times(1))
                .insertOrder(any(Order.class));
        verify(orderMapper).incrementAccountVersionIfVersionMatches(1L, 7L);
        verify(accountAuthorizer)
                .ensureAccountAccess(1L);
    }

    @Test
    void placeOrder_shouldPassCorrectOrderToValidator() {

        UUID idempotencyKey =
                UUID.fromString("d0c1a9c3-9dff-4821-ad92-d64e4d9c087e");

        PlaceOrderRequest request = mock(PlaceOrderRequest.class);

        when(request.getAccountId()).thenReturn(1L);
        when(request.getSymbol()).thenReturn("AAPL");
        when(request.getIdempotencyKey()).thenReturn(idempotencyKey);
        when(request.getSide()).thenReturn(OrderSide.BUY);
        when(request.getQuantity()).thenReturn(3);
        when(request.getPrice()).thenReturn(new BigDecimal("175.50"));

        when(instrument.getId()).thenReturn(100L);
        when(instrument.getSymbol()).thenReturn("AAPL");

        when(orderMapper.findAccountById(1L))
                .thenReturn(account);

        when(orderMapper.findInstrumentBySymbol("AAPL"))
                .thenReturn(instrument);

        when(orderMapper.findPortfolioHolding(1L, 100L))
                .thenReturn(holding);

        when(orderMapper.findExistingOrdersByAccount(1L))
                .thenReturn(Collections.emptyList());
        when(orderMapper.incrementAccountVersionIfVersionMatches(1L, 7L))
                .thenReturn(1);

        ArgumentCaptor<Order> orderCaptor =
                ArgumentCaptor.forClass(Order.class);

        orderService.placeOrder(request);

        verify(orderValidator).validate(
                orderCaptor.capture(),
                eq(account),
                eq(instrument),
                eq(holding),
                anyList()
        );

        Order capturedOrder =
                orderCaptor.getValue();

        assertNotNull(capturedOrder.getId());

        assertEquals(
                1L,
                capturedOrder.getAccountId()
        );

        assertEquals(
                100L,
                capturedOrder.getInstrumentId()
        );

        assertEquals(
                idempotencyKey,
                capturedOrder.getIdempotencyKey()
        );

        assertEquals(
                OrderSide.BUY,
                capturedOrder.getSide()
        );

        assertEquals(
                3,
                capturedOrder.getQuantity()
        );

        assertEquals(
                new BigDecimal("175.50"),
                capturedOrder.getLimitPrice()
        );

        assertEquals(
                OrderStatus.NEW,
                capturedOrder.getStatus()
        );

        assertNotNull(
                capturedOrder.getCreatedAt()
        );
        verify(accountAuthorizer)
                .ensureAccountAccess(1L);
    }

    @Test
    void placeOrder_shouldNotInsertOrder_whenValidatorThrowsException() {

        UUID idempotencyKey =
                UUID.fromString("e1d2b0d4-aeff-4922-bea3-e75f5e0d198f");

        PlaceOrderRequest request = mock(PlaceOrderRequest.class);

        when(request.getAccountId()).thenReturn(1L);
        when(request.getSymbol()).thenReturn("AAPL");
        when(request.getIdempotencyKey()).thenReturn(idempotencyKey);
        when(request.getSide()).thenReturn(OrderSide.BUY);
        when(request.getQuantity()).thenReturn(5);
        when(request.getPrice()).thenReturn(new BigDecimal("150.00"));

        when(instrument.getId()).thenReturn(100L);

        when(orderMapper.findAccountById(1L))
                .thenReturn(account);

        when(orderMapper.findInstrumentBySymbol("AAPL"))
                .thenReturn(instrument);

        when(orderMapper.findPortfolioHolding(1L, 100L))
                .thenReturn(holding);

        when(orderMapper.findExistingOrdersByAccount(1L))
                .thenReturn(Collections.emptyList());
        RuntimeException validationException =
                new RuntimeException("Validation failed");

        doThrow(validationException)
                .when(orderValidator)
                .validate(
                        any(Order.class),
                        eq(account),
                        eq(instrument),
                        eq(holding),
                        anyList()
                );

        RuntimeException exception =
                assertThrows(
                        RuntimeException.class,
                        () -> orderService.placeOrder(request)
                );

        assertEquals(
                "Validation failed",
                exception.getMessage()
        );

        verify(orderMapper, never())
                .insertOrder(any(Order.class));
        verify(accountAuthorizer)
                .ensureAccountAccess(1L);
    }

    @Test
    void placeOrder_shouldFetchExistingOrdersForIdempotencyCheck() {

        UUID idempotencyKey =
                UUID.fromString("f2e3c1e5-b0ff-4a33-8fb4-f86a6f1e2a90");

        PlaceOrderRequest request = mock(PlaceOrderRequest.class);

        when(request.getAccountId()).thenReturn(1L);
        when(request.getSymbol()).thenReturn("AAPL");
        when(request.getIdempotencyKey()).thenReturn(idempotencyKey);
        when(request.getSide()).thenReturn(OrderSide.BUY);
        when(request.getQuantity()).thenReturn(2);
        when(request.getPrice()).thenReturn(new BigDecimal("100.00"));

        when(instrument.getId()).thenReturn(100L);
        when(instrument.getSymbol()).thenReturn("AAPL");

        when(orderMapper.findAccountById(1L))
                .thenReturn(account);

        when(orderMapper.findInstrumentBySymbol("AAPL"))
                .thenReturn(instrument);

        when(orderMapper.findPortfolioHolding(1L, 100L))
                .thenReturn(holding);

        List<Order> existingOrders =
                Collections.emptyList();

        when(orderMapper.findExistingOrdersByAccount(1L))
                .thenReturn(existingOrders);
        when(orderMapper.incrementAccountVersionIfVersionMatches(1L, 7L))
                .thenReturn(1);

        orderService.placeOrder(request);

        verify(orderMapper, times(1))
                .findExistingOrdersByAccount(1L);

        verify(orderValidator).validate(
                any(Order.class),
                eq(account),
                eq(instrument),
                eq(holding),
                eq(existingOrders)
        );
        verify(accountAuthorizer)
                .ensureAccountAccess(1L);
    }

    @Test
    void placeOrder_shouldRefuseConcurrentAccountTouch() {
        UUID idempotencyKey =
                UUID.fromString("a2f3c1e5-b0ff-4a33-8fb4-f86a6f1e2a90");

        PlaceOrderRequest request = mock(PlaceOrderRequest.class);

        when(request.getAccountId()).thenReturn(1L);
        when(request.getSymbol()).thenReturn("AAPL");
        when(request.getIdempotencyKey()).thenReturn(idempotencyKey);
        when(request.getSide()).thenReturn(OrderSide.BUY);
        when(request.getQuantity()).thenReturn(2);
        when(request.getPrice()).thenReturn(new BigDecimal("100.00"));
        when(instrument.getId()).thenReturn(100L);
        when(orderMapper.findAccountById(1L)).thenReturn(account);
        when(orderMapper.findInstrumentBySymbol("AAPL")).thenReturn(instrument);
        when(orderMapper.findPortfolioHolding(1L, 100L)).thenReturn(holding);
        when(orderMapper.findExistingOrdersByAccount(1L)).thenReturn(Collections.emptyList());
        when(orderMapper.incrementAccountVersionIfVersionMatches(1L, 7L)).thenReturn(0);

        OptimisticLockException exception = assertThrows(
                OptimisticLockException.class,
                () -> orderService.placeOrder(request)
        );

        assertEquals(
                "Account version mismatch during order placement; concurrent modification detected",
                exception.getMessage()
        );
        verify(orderMapper).insertOrder(any(Order.class));
    }

    @Test
    void cancelOrder_shouldCancelOrderSuccessfully() {

        UUID orderId = UUID.randomUUID();

        Order order = mock(Order.class);

        when(orderMapper.findOrderById(orderId))
                .thenReturn(order);
        when(order.getAccountId()).thenReturn(1L);
        when(orderMapper.findAccountById(1L)).thenReturn(account);
        when(order.getStatus()).thenReturn(OrderStatus.CANCELLED);

        Instant terminalAt = Instant.now();

        when(order.getTerminalAt())
                .thenReturn(terminalAt);
        when(orderMapper.updateOrderStatusIfCurrentStatusMatches(
                orderId,
                OrderStatus.NEW,
                OrderStatus.CANCELLED,
                terminalAt
        )).thenReturn(1);
        when(orderMapper.incrementAccountVersionIfVersionMatches(1L, 7L)).thenReturn(1);

        OrderResponse response =
                orderService.cancelOrder(orderId);

        assertNotNull(response);

        assertEquals(
                orderId,
                response.getOrderId()
        );

        assertEquals(
                OrderStatus.CANCELLED,
                response.getStatus()
        );

        assertEquals(
                "Order cancelled successfully",
                response.getMessage()
        );

        verify(orderMapper, times(1))
                .findOrderById(orderId);

        verify(order, times(1))
                .cancel();

        verify(orderMapper, times(1))
                .updateOrderStatusIfCurrentStatusMatches(
                        orderId,
                        OrderStatus.NEW,
                        OrderStatus.CANCELLED,
                        terminalAt
                );
        verify(orderMapper).incrementAccountVersionIfVersionMatches(1L, 7L);
        verify(accountAuthorizer)
                .ensureAccountAccess(1L);
    }

    @Test
    void cancelOrder_shouldNotUpdateDatabase_whenCancelThrowsException() {

        UUID orderId = UUID.randomUUID();

        Order order = mock(Order.class);

        when(orderMapper.findOrderById(orderId))
                .thenReturn(order);
        when(order.getAccountId()).thenReturn(1L);
        when(orderMapper.findAccountById(1L)).thenReturn(account);

        doThrow(
                new RuntimeException("Order already terminal")
        )
                .when(order)
                .cancel();

        RuntimeException exception =
                assertThrows(
                        RuntimeException.class,
                        () -> orderService.cancelOrder(orderId)
                );

        assertEquals(
                "Order already terminal",
                exception.getMessage()
        );

        verify(order, times(1))
                .cancel();

        verify(orderMapper, never())
                .updateOrderStatusIfCurrentStatusMatches(
                        any(UUID.class),
                        any(OrderStatus.class),
                        any(OrderStatus.class),
                        any(Instant.class)
                );
        verify(accountAuthorizer)
                .ensureAccountAccess(1L);
    }

    @Test
    void cancelOrder_shouldRefuseConcurrentStateChange() {
        UUID orderId = UUID.randomUUID();
        Order order = mock(Order.class);
        Instant terminalAt = Instant.now();

        when(orderMapper.findOrderById(orderId)).thenReturn(order);
        when(order.getAccountId()).thenReturn(1L);
        when(orderMapper.findAccountById(1L)).thenReturn(account);
        when(order.getStatus()).thenReturn(OrderStatus.CANCELLED);
        when(order.getTerminalAt()).thenReturn(terminalAt);
        when(orderMapper.updateOrderStatusIfCurrentStatusMatches(
                orderId,
                OrderStatus.NEW,
                OrderStatus.CANCELLED,
                terminalAt
        )).thenReturn(0);

        OptimisticLockException exception = assertThrows(
                OptimisticLockException.class,
                () -> orderService.cancelOrder(orderId)
        );

        assertEquals(
                "Order status mismatch during cancellation; concurrent modification detected",
                exception.getMessage()
        );
        verify(orderMapper, never()).incrementAccountVersionIfVersionMatches(anyLong(), anyLong());
    }
}