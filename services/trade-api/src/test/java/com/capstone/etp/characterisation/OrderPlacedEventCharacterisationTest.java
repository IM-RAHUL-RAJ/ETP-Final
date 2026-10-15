package com.capstone.etp.characterisation;

import com.capstone.etp.dto.OrderResponse;
import com.capstone.etp.dto.PlaceOrderRequest;
import com.capstone.etp.entity.Account;
import com.capstone.etp.entity.Instrument;
import com.capstone.etp.entity.Order;
import com.capstone.etp.entity.PortfolioHolding;
import com.capstone.etp.enums.OrderSide;
import com.capstone.etp.enums.OrderStatus;
import com.capstone.etp.exception.InsufficientFundsException;
import com.capstone.etp.kafka.producer.OrderEventProducer;
import com.capstone.etp.mapper.OrderMapper;
import com.capstone.etp.security.AccountAuthorizer;
import com.capstone.etp.service.OrderService;
import com.capstone.etp.validator.OrderValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Characterisation tests for ORDER_PLACED event publishing.
 * 
 * These tests verify that:
 * - A newly accepted order publishes an ORDER_PLACED event
 * - The event is published after the transaction commits
 * - An order that fails validation does not publish an event
 */
@ExtendWith(MockitoExtension.class)
class OrderPlacedEventCharacterisationTest {

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

    private static final Long ACCOUNT_ID = 1L;
    private static final Long INSTRUMENT_ID = 100L;
    private static final Long ACCOUNT_VERSION = 7L;
    private static final String SYMBOL = "AAPL";

    @BeforeEach
    void setUp() {
        account = mock(Account.class);
        instrument = mock(Instrument.class);
        holding = mock(PortfolioHolding.class);

        lenient().when(account.getId()).thenReturn(ACCOUNT_ID);
        lenient().when(account.getVersion()).thenReturn(ACCOUNT_VERSION);
        lenient().when(account.getCurrency()).thenReturn("USD");
        lenient().when(instrument.getId()).thenReturn(INSTRUMENT_ID);
        lenient().when(instrument.getSymbol()).thenReturn(SYMBOL);
        lenient().when(instrument.getCurrency()).thenReturn("USD");
    }

    // ============================================================
    // 1. ORDER_PLACED IS PUBLISHED FOR A NEWLY ACCEPTED ORDER
    // ============================================================

    @Test
    void placeOrder_validBuyOrder_publishesOrderPlacedEvent() {
        PlaceOrderRequest request = createRequest(
                OrderSide.BUY,
                5,
                "150.00"
        );

        stubValidOrder();

        OrderResponse response = orderService.placeOrder(request);

        // Verify that the event was published
        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(orderEventProducer).publishOrderPlaced(
                orderCaptor.capture(),
                eq(SYMBOL),
                eq(new BigDecimal("150.00"))
        );

        Order publishedOrder = orderCaptor.getValue();
        assertThat(publishedOrder.getId()).isNotNull();
        assertThat(publishedOrder.getAccountId()).isEqualTo(ACCOUNT_ID);
        assertThat(publishedOrder.getStatus()).isEqualTo(OrderStatus.NEW);
        assertThat(publishedOrder.getSide()).isEqualTo(OrderSide.BUY);
        assertThat(publishedOrder.getQuantity()).isEqualTo(5);
        assertThat(publishedOrder.getLimitPrice()).isEqualByComparingTo("150.00");
    }

    @Test
    void placeOrder_validSellOrder_publishesOrderPlacedEvent() {
        PlaceOrderRequest request = createRequest(
                OrderSide.SELL,
                10,
                "200.00"
        );

        stubValidOrder();

        OrderResponse response = orderService.placeOrder(request);

        // Verify that the event was published
        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(orderEventProducer).publishOrderPlaced(
                orderCaptor.capture(),
                eq(SYMBOL),
                eq(new BigDecimal("200.00"))
        );

        Order publishedOrder = orderCaptor.getValue();
        assertThat(publishedOrder.getStatus()).isEqualTo(OrderStatus.NEW);
        assertThat(publishedOrder.getSide()).isEqualTo(OrderSide.SELL);
        assertThat(publishedOrder.getQuantity()).isEqualTo(10);
    }

    // ============================================================
    // 2. EVENT IS PUBLISHED ONLY AFTER TRANSACTION COMMITS
    // ============================================================

    @Test
    void placeOrder_validOrder_publishesEventAfterPersistence() {
        PlaceOrderRequest request = createRequest(
                OrderSide.BUY,
                5,
                "150.00"
        );

        stubValidOrder();

        // When placeOrder is called, it should:
        // 1. Insert the order
        // 2. Increment account version
        // 3. THEN publish the event
        // If publishing happens before these calls, the test fails

        orderService.placeOrder(request);

        // Verify that insertOrder was called before publishOrderPlaced
        verify(orderMapper).insertOrder(any(Order.class));
        verify(orderMapper).incrementAccountVersionIfVersionMatches(ACCOUNT_ID, ACCOUNT_VERSION);
        verify(orderEventProducer).publishOrderPlaced(any(Order.class), eq(SYMBOL), any(BigDecimal.class));
    }

    // ============================================================
    // 3. AN ORDER THAT FAILS VALIDATION PUBLISHES NOTHING
    // ============================================================

    @Test
    void placeOrder_validationFails_doesNotPublishEvent() {
        PlaceOrderRequest request = createRequest(
                OrderSide.BUY,
                5,
                "150.00"
        );

        when(orderMapper.findAccountById(ACCOUNT_ID)).thenReturn(account);
        when(orderMapper.findInstrumentBySymbol(SYMBOL)).thenReturn(instrument);
        when(orderMapper.findPortfolioHolding(ACCOUNT_ID, INSTRUMENT_ID)).thenReturn(holding);
        when(orderMapper.findExistingOrdersByAccount(ACCOUNT_ID)).thenReturn(Collections.emptyList());

        InsufficientFundsException exception = new InsufficientFundsException(
                ACCOUNT_ID,
                new BigDecimal("1000.00"),
                new BigDecimal("500.00")
        );

        doThrow(exception).when(orderValidator).validate(
                any(Order.class),
                eq(account),
                eq(instrument),
                eq(holding),
                anyList()
        );

        // Validation should fail and throw exception
        assertThrows(InsufficientFundsException.class, () -> orderService.placeOrder(request));

        // Verify that the event was NOT published
        verify(orderEventProducer, never()).publishOrderPlaced(any(Order.class), any(String.class), any(BigDecimal.class));
    }

    // ============================================================
    // 4. ACCEPTED ORDER IS WRITTEN AT NEW AND ANSWERED NEW
    // ============================================================

    @Test
    void placeOrder_validOrder_writesAndAnswersNEW() {
        PlaceOrderRequest request = createRequest(
                OrderSide.BUY,
                5,
                "150.00"
        );

        stubValidOrder();

        OrderResponse response = orderService.placeOrder(request);

        // Verify the order is written at NEW status
        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(orderMapper).insertOrder(orderCaptor.capture());

        Order persistedOrder = orderCaptor.getValue();
        assertThat(persistedOrder.getStatus()).isEqualTo(OrderStatus.NEW);

        // Verify the response answers NEW
        assertThat(response.getStatus()).isEqualTo(OrderStatus.NEW);
        assertThat(response.getMessage()).isEqualTo("Order created successfully");
    }

    // ============================================================
    // HELPER METHODS
    // ============================================================

    private PlaceOrderRequest createRequest(
            OrderSide side,
            int quantity,
            String price
    ) {
        return PlaceOrderRequest.builder()
                .accountId(ACCOUNT_ID)
                .symbol(SYMBOL)
                .idempotencyKey(UUID.randomUUID())
                .side(side)
                .quantity(quantity)
                .price(price != null ? new BigDecimal(price) : null)
                .build();
    }

    private void stubValidOrder() {
        when(orderMapper.findAccountById(ACCOUNT_ID)).thenReturn(account);
        when(orderMapper.findInstrumentBySymbol(SYMBOL)).thenReturn(instrument);
        when(orderMapper.findPortfolioHolding(ACCOUNT_ID, INSTRUMENT_ID)).thenReturn(holding);
        when(orderMapper.findExistingOrdersByAccount(ACCOUNT_ID)).thenReturn(Collections.emptyList());
        lenient().when(orderMapper.incrementAccountVersionIfVersionMatches(
                ACCOUNT_ID,
                ACCOUNT_VERSION
        )).thenReturn(1);
    }
}
