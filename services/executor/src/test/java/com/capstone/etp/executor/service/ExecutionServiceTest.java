package com.capstone.etp.executor.service;

import com.capstone.etp.executor.client.FauxnanceClient;
import com.capstone.etp.executor.entity.Account;
import com.capstone.etp.executor.entity.Instrument;
import com.capstone.etp.executor.entity.Order;
import com.capstone.etp.executor.entity.PortfolioHolding;
import com.capstone.etp.executor.enums.AccountStatus;
import com.capstone.etp.executor.enums.OrderSide;
import com.capstone.etp.executor.enums.OrderStatus;
import com.capstone.etp.executor.exception.OptimisticLockRetryException;
import com.capstone.etp.executor.exception.PriceUnavailableException;
import com.capstone.etp.executor.kafka.models.OrderPlacedPayload;
import com.capstone.etp.executor.kafka.models.QuotePayload;
import com.capstone.etp.executor.mapper.ExecutorOrderMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.RecoverableDataAccessException;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ExecutionService} and {@link SettlementService} using
 * Mockito to replace the mapper and FauxnanceClient.
 *
 * <p>Because SettlementService is instantiated directly (not via Spring), the
 * {@code @Transactional} proxy is absent — the method runs but without a
 * transaction wrapper.  That is intentional: we are testing business behaviour,
 * not transaction demarcation.
 *
 * <p>Covered paths:
 * <ol>
 *   <li>Order already settled (status != NEW) → alreadyHandled, no DB writes</li>
 *   <li>Account suspended after acceptance → ORDER_REJECTED / ACCOUNT_NOT_ACTIVE</li>
 *   <li>Cross-currency BUY uses payload limit in instrument currency and settles in account currency</li>
 *   <li>Insufficient funds at execution time (BUY, limit price) → ORDER_REJECTED</li>
 *   <li>Insufficient holdings at execution time (SELL) → ORDER_REJECTED</li>
 *   <li>Price unavailable → ORDER_REJECTED / PRICE_UNAVAILABLE</li>
 *   <li>Price not met (BUY limit < ask) → ORDER_REJECTED / PRICE_NOT_MET</li>
 *   <li>Successful BUY fill → FILLED, cash debited, holding upserted</li>
 *   <li>Successful SELL fill → FILLED, cash credited, holding decreased</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
class ExecutionServiceTest {

    @Mock
    private ExecutorOrderMapper mapper;

    @Mock
    private FauxnanceClient fauxnanceClient;

    private SettlementService settlementService;
    private ExecutionService  executionService;

    // -- Fixture data ---------------------------------------------------------

    private static final UUID    ORDER_ID   = UUID.randomUUID();
    private static final Long    ACCOUNT_ID = 1L;
    private static final Long    INSTR_ID   = 10L;
    private static final String  SYMBOL     = "AAPL";

    @BeforeEach
    void setUp() {
        settlementService = new SettlementService(mapper);
        executionService  = new ExecutionService(mapper, fauxnanceClient, settlementService);
        // Inject the @Value field manually
        setField(executionService, "maxLockAttempts", 3);
    }

    // -------------------------------------------------------------------------
    // 1. Duplicate delivery
    // -------------------------------------------------------------------------

    @Test
    void alreadySettledOrder_returnsAlreadyHandled_noWrites() {
        when(mapper.findOrderById(ORDER_ID)).thenReturn(filledOrder());

        SettlementResult result = executionService.execute(payload());

        assertThat(result.alreadyHandled()).isTrue();
        verify(mapper, never()).updateAccountCashAndVersion(any(), any(), any());
        verify(mapper, never()).insertTransaction(any(), anyInt(), any(), any(), any(), any());
    }

    // -------------------------------------------------------------------------
    // 2. Account suspended after acceptance
    // -------------------------------------------------------------------------

    @Test
    void accountSuspended_afterAcceptance_rejectsWithAccountNotActive() {
        stubNewBuyOrder("235.00", 100);
        stubTradableInstrument("USD");
        stubQuote("232.77", "232.65");
        stubSettleOrderStatus(OrderStatus.REJECTED);
        when(mapper.findAccountById(ACCOUNT_ID)).thenReturn(suspendedAccount("50000", "USD"));

        SettlementResult result = executionService.execute(payload());

        assertThat(result.status()).isEqualTo(OrderStatus.REJECTED);
        assertThat(result.reason()).isEqualTo("ACCOUNT_NOT_ACTIVE");
        verify(mapper, never()).updateAccountCashAndVersion(any(), any(), any());
    }

    // -------------------------------------------------------------------------
    // 3. Cross-currency conversion path
    // -------------------------------------------------------------------------

    @Test
    void crossCurrencyBuy_convertsCashDeltaToAccountCurrency() {
        stubNewBuyOrder("1.00", 10);
        stubTradableInstrument("INR");
        stubQuote("95.00", "94.50");
        stubSettleOrderStatus(OrderStatus.FILLED);
        Account account = activeAccount("1000.00", "USD");
        when(mapper.findAccountById(ACCOUNT_ID)).thenReturn(account);
        when(mapper.findHolding(ACCOUNT_ID, INSTR_ID)).thenReturn(null);
        when(mapper.findPortfolioIdByAccountId(ACCOUNT_ID)).thenReturn(99L);
        when(mapper.updateAccountCashAndVersion(eq(ACCOUNT_ID), any(), eq(account.getVersion())))
                .thenReturn(1);

        SettlementResult result = executionService.execute(payload("96.00"));

        assertThat(result.status()).isEqualTo(OrderStatus.FILLED);
        assertThat(result.executedPrice()).isEqualByComparingTo("1.00");
        verify(mapper).updateAccountCashAndVersion(
                eq(ACCOUNT_ID),
                eq(new BigDecimal("990.00")),
                eq(account.getVersion()));
    }

    // -------------------------------------------------------------------------
    // 4. Insufficient funds at limit price (BUY)
    // -------------------------------------------------------------------------

    @Test
    void insufficientFunds_atLimitPrice_rejectsWithInsufficientFunds() {
        // Limit 235.00 * 100 = 23500, but account only has 100
        stubNewBuyOrder("235.00", 100);
        stubTradableInstrument("USD");
        stubQuote("232.77", "232.65");
        stubSettleOrderStatus(OrderStatus.REJECTED);
        when(mapper.findAccountById(ACCOUNT_ID)).thenReturn(activeAccount("100.00", "USD"));

        SettlementResult result = executionService.execute(payload());

        assertThat(result.status()).isEqualTo(OrderStatus.REJECTED);
        assertThat(result.reason()).isEqualTo("INSUFFICIENT_FUNDS");
        assertThat(result.executedPrice()).isEqualByComparingTo("0.00");
        verify(mapper).insertTransaction(
                eq(ORDER_ID),
                eq(100),
                eq(new BigDecimal("0.00")),
                eq("FAILED"),
                eq("INSUFFICIENT_FUNDS"),
                any());
    }

    // -------------------------------------------------------------------------
    // 5. Insufficient holdings at execution time (SELL)
    // -------------------------------------------------------------------------

    @Test
    void insufficientHoldings_atExecutionTime_rejectsWithInsufficientHoldings() {
        stubNewSellOrder("230.00", 100);
        stubTradableInstrument("USD");
        stubQuote("232.77", "232.65");
        stubSettleOrderStatus(OrderStatus.REJECTED);
        when(mapper.findAccountById(ACCOUNT_ID)).thenReturn(activeAccount("50000", "USD"));
        // Account holds only 50 shares, but the order is for 100
        when(mapper.findHolding(ACCOUNT_ID, INSTR_ID))
                .thenReturn(PortfolioHolding.builder().quantity(50).avgPrice(new BigDecimal("220.00")).build());

        SettlementResult result = executionService.execute(payload());

        assertThat(result.status()).isEqualTo(OrderStatus.REJECTED);
        assertThat(result.reason()).isEqualTo("INSUFFICIENT_HOLDINGS");
    }

    // -------------------------------------------------------------------------
    // 6. Price unavailable (Fauxnance outage)
    // -------------------------------------------------------------------------

    @Test
    void priceUnavailable_afterRetries_rejectsWithPriceUnavailable() {
        stubNewBuyOrder("235.00", 100);
        stubTradableInstrument("USD");
        when(fauxnanceClient.getQuote(SYMBOL)).thenThrow(new PriceUnavailableException("503"));
        stubSettleOrderStatus(OrderStatus.REJECTED);
        when(mapper.findAccountById(ACCOUNT_ID)).thenReturn(activeAccount("50000", "USD"));

        SettlementResult result = executionService.execute(payload());

        assertThat(result.status()).isEqualTo(OrderStatus.REJECTED);
        assertThat(result.reason()).isEqualTo("PRICE_UNAVAILABLE");
    }

    // -------------------------------------------------------------------------
    // 7. Price not met (BUY limit < ask)
    // -------------------------------------------------------------------------

    @Test
    void priceNotMet_buyLimitBelowAsk_rejectsWithPriceNotMet() {
        stubNewBuyOrder("230.00", 100); // limit 230 < ask 232.77
        stubTradableInstrument("USD");
        stubQuote("232.77", "232.65");
        stubSettleOrderStatus(OrderStatus.REJECTED);
        when(mapper.findAccountById(ACCOUNT_ID)).thenReturn(activeAccount("50000", "USD"));

        SettlementResult result = executionService.execute(payload());

        assertThat(result.status()).isEqualTo(OrderStatus.REJECTED);
        assertThat(result.reason()).isEqualTo("PRICE_NOT_MET");
    }

    // -------------------------------------------------------------------------
    // 8. Successful BUY fill
    // -------------------------------------------------------------------------

    @Test
    void successfulBuyFill_debitsAsk_upsertHolding() {
        stubNewBuyOrder("235.00", 100);
        stubTradableInstrument("USD");
        stubQuote("232.77", "232.65");
        stubSettleOrderStatus(OrderStatus.FILLED);

        BigDecimal cashBalance = new BigDecimal("50000.00");
        Account account = activeAccount(cashBalance.toPlainString(), "USD");
        when(mapper.findAccountById(ACCOUNT_ID)).thenReturn(account);
        when(mapper.findHolding(ACCOUNT_ID, INSTR_ID)).thenReturn(null); // no existing holding
        when(mapper.findPortfolioIdByAccountId(ACCOUNT_ID)).thenReturn(99L);
        when(mapper.updateAccountCashAndVersion(eq(ACCOUNT_ID), any(), eq(account.getVersion())))
                .thenReturn(1);

        SettlementResult result = executionService.execute(payload());

        assertThat(result.status()).isEqualTo(OrderStatus.FILLED);
        assertThat(result.executedPrice()).isEqualByComparingTo("232.77");

        // Cash debited: 232.77 * 100 = 23277.00
        BigDecimal expectedBalance = cashBalance.subtract(new BigDecimal("23277.00"));
        verify(mapper).updateAccountCashAndVersion(
                eq(ACCOUNT_ID), eq(expectedBalance), eq(account.getVersion()));

        // Holding upserted with executed price
        verify(mapper).upsertHolding(eq(99L), eq(INSTR_ID), eq(100), eq(new BigDecimal("232.77")));

        // Transaction record inserted
        verify(mapper).insertTransaction(eq(ORDER_ID), eq(100),
                eq(new BigDecimal("232.77")), eq("EXECUTED"), eq(null), any());

        // Result carries correct cashDelta (negative for BUY)
        assertThat(result.cashDelta()).isEqualByComparingTo("-23277.00");
        assertThat(result.positionQuantityAfter()).isEqualTo(100);
    }

    // -------------------------------------------------------------------------
    // 9. Successful SELL fill
    // -------------------------------------------------------------------------

    @Test
    void successfulSellFill_creditsBid_decreasesHolding() {
        stubNewSellOrder("230.00", 50);
        stubTradableInstrument("USD");
        stubQuote("232.77", "232.65");
        stubSettleOrderStatus(OrderStatus.FILLED);

        Account account = activeAccount("10000.00", "USD");
        when(mapper.findAccountById(ACCOUNT_ID)).thenReturn(account);
        // Existing holding: 200 shares at 220.00 avg
        when(mapper.findHolding(ACCOUNT_ID, INSTR_ID))
                .thenReturn(PortfolioHolding.builder()
                        .portfolioId(99L).instrumentId(INSTR_ID)
                        .quantity(200).avgPrice(new BigDecimal("220.00"))
                        .build());
        when(mapper.findPortfolioIdByAccountId(ACCOUNT_ID)).thenReturn(99L);
        when(mapper.updateAccountCashAndVersion(eq(ACCOUNT_ID), any(), eq(account.getVersion())))
                .thenReturn(1);

        SettlementResult result = executionService.execute(payload());

        assertThat(result.status()).isEqualTo(OrderStatus.FILLED);
        assertThat(result.executedPrice()).isEqualByComparingTo("232.65");

        // Cash credited: 232.65 * 50 = 11632.50
        BigDecimal expectedBalance = new BigDecimal("10000.00").add(new BigDecimal("11632.50"));
        verify(mapper).updateAccountCashAndVersion(
                eq(ACCOUNT_ID), eq(expectedBalance), eq(account.getVersion()));

        // Holding decreased (not upserted)
        verify(mapper).decreaseHolding(eq(ACCOUNT_ID), eq(INSTR_ID), eq(50));
        verify(mapper, never()).upsertHolding(any(), any(), anyInt(), any());

        // cashDelta is positive for SELL
        assertThat(result.cashDelta()).isEqualByComparingTo("11632.50");
        assertThat(result.positionQuantityAfter()).isEqualTo(150); // 200 - 50
    }

    @Test
    void optimisticLockConflict_retriesAndThenSucceeds() {
        stubNewBuyOrder("235.00", 100);
        stubTradableInstrument("INR");
        stubQuote("232.77", "232.65");

        Account staleAccount = activeAccount("50000.00", "INR");
        Account freshAccount = activeAccount("50000.00", "INR");
        freshAccount.setVersion(1L);
        when(mapper.findAccountById(ACCOUNT_ID)).thenReturn(staleAccount, freshAccount);
        when(mapper.findHolding(ACCOUNT_ID, INSTR_ID)).thenReturn(null);
        when(mapper.findPortfolioIdByAccountId(ACCOUNT_ID)).thenReturn(99L);
        when(mapper.settleOrderStatus(eq(ORDER_ID), eq(OrderStatus.FILLED), any())).thenReturn(1, 1);
        when(mapper.updateAccountCashAndVersion(eq(ACCOUNT_ID), any(), eq(0L))).thenReturn(0);
        when(mapper.updateAccountCashAndVersion(eq(ACCOUNT_ID), any(), eq(1L))).thenReturn(1);

        SettlementResult result = executionService.execute(payload());

        assertThat(result.status()).isEqualTo(OrderStatus.FILLED);
        verify(mapper).updateAccountCashAndVersion(eq(ACCOUNT_ID), eq(new BigDecimal("26723.00")), eq(1L));
    }

    @Test
    void optimisticLockBudgetExhausted_throwsRecoverableDataAccessException() {
        stubNewBuyOrder("235.00", 100);
        stubTradableInstrument("INR");
        stubQuote("232.77", "232.65");
        when(mapper.findAccountById(ACCOUNT_ID)).thenReturn(activeAccount("50000.00", "INR"));
        when(mapper.findHolding(ACCOUNT_ID, INSTR_ID)).thenReturn(null);
        when(mapper.settleOrderStatus(eq(ORDER_ID), eq(OrderStatus.FILLED), any())).thenReturn(1, 1, 1);
        when(mapper.updateAccountCashAndVersion(eq(ACCOUNT_ID), any(), any())).thenReturn(0);

        assertThatThrownBy(() -> executionService.execute(payload()))
                .isInstanceOf(RecoverableDataAccessException.class)
                .hasCauseInstanceOf(OptimisticLockRetryException.class)
                .hasMessageContaining("Exhausted optimistic lock retry budget");
    }

    // =========================================================================
    // Stub helpers
    // =========================================================================

    private OrderPlacedPayload payload() {
        return payload(null);
    }

    private OrderPlacedPayload payload(String price) {
        OrderPlacedPayload p = new OrderPlacedPayload();
        p.setOrderId(ORDER_ID);
        p.setAccountId(ACCOUNT_ID);
        p.setSymbol(SYMBOL);
        if (price != null) {
            p.setPrice(new BigDecimal(price));
        }
        return p;
    }

    private void stubNewBuyOrder(String limitPrice, int qty) {
        when(mapper.findOrderById(ORDER_ID)).thenReturn(Order.builder()
                .id(ORDER_ID).accountId(ACCOUNT_ID).instrumentId(INSTR_ID)
                .side(OrderSide.BUY).quantity(qty)
                .limitPrice(new BigDecimal(limitPrice))
                .status(OrderStatus.NEW)
                .build());
    }

    private void stubNewSellOrder(String limitPrice, int qty) {
        when(mapper.findOrderById(ORDER_ID)).thenReturn(Order.builder()
                .id(ORDER_ID).accountId(ACCOUNT_ID).instrumentId(INSTR_ID)
                .side(OrderSide.SELL).quantity(qty)
                .limitPrice(new BigDecimal(limitPrice))
                .status(OrderStatus.NEW)
                .build());
    }

    private void stubTradableInstrument(String currency) {
        when(mapper.findInstrumentById(INSTR_ID)).thenReturn(
                Instrument.builder().id(INSTR_ID).symbol(SYMBOL).currency(currency).tradable(true).build());
    }

    private void stubQuote(String ask, String bid) {
        QuotePayload q = new QuotePayload();
        q.setAsk(new BigDecimal(ask));
        q.setBid(new BigDecimal(bid));
        q.setSymbol(SYMBOL);
        when(fauxnanceClient.getQuote(SYMBOL)).thenReturn(q);
    }

    private void stubSettleOrderStatus(OrderStatus status) {
        when(mapper.settleOrderStatus(eq(ORDER_ID), eq(status), any())).thenReturn(1);
    }

    private static Order filledOrder() {
        return Order.builder().id(ORDER_ID).accountId(ACCOUNT_ID)
                .status(OrderStatus.FILLED).build();
    }

    private static Account activeAccount(String balance, String currency) {
        return Account.builder()
                .id(ACCOUNT_ID).status(AccountStatus.ACTIVE)
                .currency(currency)
                .cashBalance(new BigDecimal(balance)).version(0L)
                .build();
    }

    private static Account suspendedAccount(String balance, String currency) {
        return Account.builder()
                .id(ACCOUNT_ID).status(AccountStatus.SUSPENDED)
                .currency(currency)
                .cashBalance(new BigDecimal(balance)).version(0L)
                .build();
    }

    /** Reflective setter for @Value fields that are not constructor-injected. */
    private static void setField(Object target, String name, Object value) {
        try {
            var field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}
