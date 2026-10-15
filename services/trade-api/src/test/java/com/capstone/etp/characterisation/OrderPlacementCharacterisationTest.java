package com.capstone.etp.characterisation;
import com.capstone.etp.dto.OrderResponse;
import com.capstone.etp.dto.PlaceOrderRequest;
import com.capstone.etp.entity.Account;
import com.capstone.etp.entity.Instrument;
import com.capstone.etp.entity.Order;
import com.capstone.etp.entity.PortfolioHolding;
import com.capstone.etp.enums.OrderSide;
import com.capstone.etp.enums.OrderStatus;
import com.capstone.etp.exception.AccountNotActiveException;
import com.capstone.etp.exception.DuplicateOrderException;
import com.capstone.etp.exception.InsufficientFundsException;
import com.capstone.etp.exception.InsufficientHoldingsException;
import com.capstone.etp.exception.InstrumentNotFoundException;
import com.capstone.etp.exception.InvalidOrderValueException;
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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderPlacementCharacterisationTest {

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
        // Mock the account, instrument, and holding objects
        account = mock(Account.class);
        instrument = mock(Instrument.class);
        holding = mock(PortfolioHolding.class);

        lenient()
                .when(account.getId())
                .thenReturn(ACCOUNT_ID);

        lenient()
                .when(account.getVersion())
                .thenReturn(ACCOUNT_VERSION);
        lenient()
                .when(account.getCurrency())
                .thenReturn("USD");

        lenient()
                .when(instrument.getId())
                .thenReturn(INSTRUMENT_ID);

        lenient()
                .when(instrument.getSymbol())
                .thenReturn(SYMBOL);
        lenient()
                .when(instrument.getCurrency())
                .thenReturn("USD");
    }

    // ============================================================
    // 1. SUCCESSFUL BUY
    // ============================================================

    @Test
    void placeOrder_validBuyOrder_createsNewOrder() {

        PlaceOrderRequest request =
                createRequest(
                        OrderSide.BUY,
                        5,
                        "150.00"
                );

        stubValidOrder();

        OrderResponse response =
                orderService.placeOrder(request);

        // Pin field-by-field response
        assertThat(response).isNotNull();
        assertThat(response.getOrderId()).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OrderStatus.NEW);
        assertThat(response.getMessage()).isEqualTo("Order created successfully");
        assertThat(response.getSymbol()).isEqualTo(SYMBOL);
        assertThat(response.getSide()).isEqualTo(OrderSide.BUY);
        assertThat(response.getQuantity()).isEqualTo(5);
        assertThat(response.getPrice()).isEqualByComparingTo("150.00");

        verify(accountAuthorizer)
                .ensureAccountAccess(ACCOUNT_ID);

        verify(orderValidator)
                .validate(
                        any(Order.class),
                        eq(account),
                        eq(instrument),
                        eq(holding),
                        anyList()
                );

        // Pin what is written to the order row
        ArgumentCaptor<Order> orderCaptor =
                ArgumentCaptor.forClass(Order.class);
        verify(orderMapper)
                .insertOrder(orderCaptor.capture());

        Order capturedOrder = orderCaptor.getValue();
        assertThat(capturedOrder.getId()).isNotNull();
        assertThat(capturedOrder.getAccountId()).isEqualTo(ACCOUNT_ID);
        assertThat(capturedOrder.getInstrumentId()).isEqualTo(INSTRUMENT_ID);
        assertThat(capturedOrder.getIdempotencyKey()).isEqualTo(request.getIdempotencyKey());
        assertThat(capturedOrder.getSide()).isEqualTo(OrderSide.BUY);
        assertThat(capturedOrder.getQuantity()).isEqualTo(5);
        assertThat(capturedOrder.getLimitPrice()).isEqualByComparingTo("150.00");
        assertThat(capturedOrder.getStatus()).isEqualTo(OrderStatus.NEW);
        assertThat(capturedOrder.getCreatedAt()).isNotNull();
        assertThat(capturedOrder.getTerminalAt()).isNull();

        // Pin current Sprint 6 behavior: only account version is incremented.
        // Cash and portfolio position are NOT modified upon order placement (only upon execution).
        verify(orderMapper)
                .incrementAccountVersionIfVersionMatches(
                        ACCOUNT_ID,
                        ACCOUNT_VERSION
                );
    }

    // ============================================================
    // 2. SUCCESSFUL SELL
    // ============================================================

    @Test
    void placeOrder_validSellOrder_createsNewOrder() {

        PlaceOrderRequest request =
                createRequest(
                        OrderSide.SELL,
                        5,
                        "160.00"
                );

        stubValidOrder();

        OrderResponse response =
                orderService.placeOrder(request);

        // Pin field-by-field response
        assertThat(response).isNotNull();
        assertThat(response.getOrderId()).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OrderStatus.NEW);
        assertThat(response.getMessage()).isEqualTo("Order created successfully");
        assertThat(response.getSymbol()).isEqualTo(SYMBOL);
        assertThat(response.getSide()).isEqualTo(OrderSide.SELL);
        assertThat(response.getQuantity()).isEqualTo(5);
        assertThat(response.getPrice()).isEqualByComparingTo("160.00");

        verify(accountAuthorizer)
                .ensureAccountAccess(ACCOUNT_ID);

        verify(orderValidator)
                .validate(
                        any(Order.class),
                        eq(account),
                        eq(instrument),
                        eq(holding),
                        anyList()
                );

        // Pin what is written to the order row
        ArgumentCaptor<Order> orderCaptor =
                ArgumentCaptor.forClass(Order.class);
        verify(orderMapper)
                .insertOrder(orderCaptor.capture());

        Order capturedOrder = orderCaptor.getValue();
        assertThat(capturedOrder.getId()).isNotNull();
        assertThat(capturedOrder.getAccountId()).isEqualTo(ACCOUNT_ID);
        assertThat(capturedOrder.getInstrumentId()).isEqualTo(INSTRUMENT_ID);
        assertThat(capturedOrder.getIdempotencyKey()).isEqualTo(request.getIdempotencyKey());
        assertThat(capturedOrder.getSide()).isEqualTo(OrderSide.SELL);
        assertThat(capturedOrder.getQuantity()).isEqualTo(5);
        assertThat(capturedOrder.getLimitPrice()).isEqualByComparingTo("160.00");
        assertThat(capturedOrder.getStatus()).isEqualTo(OrderStatus.NEW);
        assertThat(capturedOrder.getCreatedAt()).isNotNull();
        assertThat(capturedOrder.getTerminalAt()).isNull();

        // Pin current Sprint 6 behavior: only account version is incremented.
        // Cash and portfolio position are NOT modified upon order placement.
        verify(orderMapper)
                .incrementAccountVersionIfVersionMatches(
                        ACCOUNT_ID,
                        ACCOUNT_VERSION
                );
    }

    // ============================================================
    // 3. DUPLICATE ORDER
    // ============================================================

    @Test
    void placeOrder_duplicateKey_throwsDuplicateOrderException() {

        PlaceOrderRequest request =
                createRequest(
                        OrderSide.BUY,
                        5,
                        "150.00"
                );

        Order existingOrder = mock(Order.class);

        when(orderMapper.findAccountById(ACCOUNT_ID))
                .thenReturn(account);

        when(orderMapper.findInstrumentBySymbol(SYMBOL))
                .thenReturn(instrument);

        when(orderMapper.findPortfolioHolding(
                ACCOUNT_ID,
                INSTRUMENT_ID
        )).thenReturn(holding);

        when(orderMapper.findExistingOrdersByAccount(
                ACCOUNT_ID
        )).thenReturn(
                Collections.singletonList(existingOrder)
        );

        DuplicateOrderException duplicateException =
                new DuplicateOrderException(request.getIdempotencyKey());

        doThrow(duplicateException)
                .when(orderValidator)
                .validate(
                        any(Order.class),
                        eq(account),
                        eq(instrument),
                        eq(holding),
                        anyList()
                );

        DuplicateOrderException thrown = assertThrows(
                DuplicateOrderException.class,
                () -> orderService.placeOrder(request)
        );

        // Pin error code and idempotency key
        assertThat(thrown.getCatalogueCode()).isEqualTo("ORD-409");
        assertThat(thrown.getIdempotencyKey()).isEqualTo(request.getIdempotencyKey());

        verify(orderMapper)
                .findExistingOrdersByAccount(ACCOUNT_ID);

        verify(orderValidator)
                .validate(
                        any(Order.class),
                        eq(account),
                        eq(instrument),
                        eq(holding),
                        anyList()
                );

        verify(orderMapper, never())
                .insertOrder(any(Order.class));
    }

    // ============================================================
    // 4. UNAFFORDABLE BUY
    // ============================================================

    @Test
    void placeOrder_unaffordableBuy_throwsInsufficientFundsException() {

        PlaceOrderRequest request =
                createRequest(
                        OrderSide.BUY,
                        5,
                        "150.00"
                );

        stubValidOrder();

        InsufficientFundsException exception =
                new InsufficientFundsException(
                        ACCOUNT_ID,
                        new BigDecimal("1000.00"),
                        new BigDecimal("500.00")
                );

        doThrow(exception)
                .when(orderValidator)
                .validate(
                        any(Order.class),
                        eq(account),
                        eq(instrument),
                        eq(holding),
                        anyList()
                );

        InsufficientFundsException thrown = assertThrows(
                InsufficientFundsException.class,
                () -> orderService.placeOrder(request)
        );

        // Pin error code and account ref
        assertThat(thrown.getCatalogueCode()).isEqualTo("ORD-400");
        assertThat(thrown.getaccountId()).isEqualTo(ACCOUNT_ID);

        verify(orderValidator)
                .validate(
                        any(Order.class),
                        eq(account),
                        eq(instrument),
                        eq(holding),
                        anyList()
                );

        verify(orderMapper, never())
                .insertOrder(any(Order.class));
    }

    // ============================================================
    // 5. UNKNOWN SYMBOL
    // ============================================================

    @Test
    void placeOrder_unknownSymbol_throwsInstrumentNotFoundException() {

        PlaceOrderRequest request =
                createRequest(
                        OrderSide.BUY,
                        5,
                        "150.00"
                );

        when(orderMapper.findAccountById(ACCOUNT_ID))
                .thenReturn(account);

        when(orderMapper.findInstrumentBySymbol(SYMBOL))
                .thenReturn(null);

        InstrumentNotFoundException thrown = assertThrows(
                InstrumentNotFoundException.class,
                () -> orderService.placeOrder(request)
        );

        // Pin error code
        assertThat(thrown.getCatalogueCode()).isEqualTo("INS-404");

        verify(orderMapper)
                .findAccountById(ACCOUNT_ID);

        verify(orderMapper)
                .findInstrumentBySymbol(SYMBOL);

        verify(orderMapper, never())
                .findPortfolioHolding(
                        anyLong(),
                        anyLong()
                );

        verify(orderValidator, never())
                .validate(
                        any(),
                        any(),
                        any(),
                        any(),
                        anyList()
                );

        verify(orderMapper, never())
                .insertOrder(any(Order.class));
    }

    // ============================================================
    // 6. INACTIVE ACCOUNT
    // ============================================================

    @Test
    void placeOrder_inactiveAccount_throwsAccountNotActiveException() {

        PlaceOrderRequest request =
                createRequest(
                        OrderSide.BUY,
                        5,
                        "150.00"
                );

        AccountNotActiveException exception =
                new AccountNotActiveException(ACCOUNT_ID, "SUSPENDED");

        doThrow(exception)
                .when(accountAuthorizer)
                .ensureAccountAccess(ACCOUNT_ID);

        AccountNotActiveException thrown = assertThrows(
                AccountNotActiveException.class,
                () -> orderService.placeOrder(request)
        );

        // Pin error code and status
        assertThat(thrown.getCatalogueCode()).isEqualTo("ACC-403");
        assertThat(thrown.getAccountRef()).isEqualTo(ACCOUNT_ID);
        assertThat(thrown.getCurrentStatus()).isEqualTo("SUSPENDED");

        verify(accountAuthorizer)
                .ensureAccountAccess(ACCOUNT_ID);

        verify(orderMapper, never())
                .findInstrumentBySymbol(any());

        verify(orderMapper, never())
                .insertOrder(any(Order.class));

        verify(orderValidator, never())
                .validate(
                        any(),
                        any(),
                        any(),
                        any(),
                        anyList()
                );
    }

    // ============================================================
    // 7. INSUFFICIENT HOLDINGS
    // ============================================================

    @Test
    void placeOrder_insufficientHoldings_doesNotCreateOrder() {

        PlaceOrderRequest request =
                createRequest(
                        OrderSide.SELL,
                        100,
                        "160.00"
                );

        stubValidOrder();

        InsufficientHoldingsException exception =
                new InsufficientHoldingsException(
                        ACCOUNT_ID,
                        SYMBOL,
                        100L,
                        50L
                );

        doThrow(exception)
                .when(orderValidator)
                .validate(
                        any(Order.class),
                        eq(account),
                        eq(instrument),
                        eq(holding),
                        anyList()
                );

        InsufficientHoldingsException thrown = assertThrows(
                InsufficientHoldingsException.class,
                () -> orderService.placeOrder(request)
        );

        // Pin error code and properties
        assertThat(thrown.getCatalogueCode()).isEqualTo("ORD-409");
        assertThat(thrown.getaccountId()).isEqualTo(ACCOUNT_ID);
        assertThat(thrown.getSymbol()).isEqualTo(SYMBOL);

        verify(orderValidator)
                .validate(
                        any(Order.class),
                        eq(account),
                        eq(instrument),
                        eq(holding),
                        anyList()
                );

        verify(orderMapper, never())
                .insertOrder(any(Order.class));
    }

    // ============================================================
    // 8. ZERO QUANTITY
    // ============================================================

    @Test
    void placeOrder_zeroQuantity_throwsInvalidOrderValueException() {

        PlaceOrderRequest request =
                createRequest(
                        OrderSide.BUY,
                        0,
                        "150.00"
                );

        stubValidOrder();

        InvalidOrderValueException exception =
                mock(InvalidOrderValueException.class);

        doThrow(exception)
                .when(orderValidator)
                .validate(
                        any(Order.class),
                        eq(account),
                        eq(instrument),
                        eq(holding),
                        anyList()
                );

        assertThrows(
                InvalidOrderValueException.class,
                () -> orderService.placeOrder(request)
        );

        verify(orderValidator)
                .validate(
                        any(Order.class),
                        eq(account),
                        eq(instrument),
                        eq(holding),
                        anyList()
                );

        verify(orderMapper, never())
                .insertOrder(any(Order.class));
    }

    // ============================================================
    // 9. NEGATIVE PRICE
    // ============================================================

    @Test
    void placeOrder_negativePrice_throwsInvalidOrderValueException() {

        PlaceOrderRequest request =
                createRequest(
                        OrderSide.BUY,
                        5,
                        "-150.00"
                );

        stubValidOrder();

        InvalidOrderValueException exception =
                mock(InvalidOrderValueException.class);

        doThrow(exception)
                .when(orderValidator)
                .validate(
                        any(Order.class),
                        eq(account),
                        eq(instrument),
                        eq(holding),
                        anyList()
                );

        assertThrows(
                InvalidOrderValueException.class,
                () -> orderService.placeOrder(request)
        );

        verify(orderValidator)
                .validate(
                        any(Order.class),
                        eq(account),
                        eq(instrument),
                        eq(holding),
                        anyList()
                );

        verify(orderMapper, never())
                .insertOrder(any(Order.class));
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

        when(orderMapper.findAccountById(ACCOUNT_ID))
                .thenReturn(account);

        when(orderMapper.findInstrumentBySymbol(SYMBOL))
                .thenReturn(instrument);

        when(orderMapper.findPortfolioHolding(
                ACCOUNT_ID,
                INSTRUMENT_ID
        )).thenReturn(holding);

        when(orderMapper.findExistingOrdersByAccount(
                ACCOUNT_ID
        )).thenReturn(Collections.emptyList());

        lenient()
                .when(orderMapper.incrementAccountVersionIfVersionMatches(
                        ACCOUNT_ID,
                        ACCOUNT_VERSION
                )).thenReturn(1);
    }
}