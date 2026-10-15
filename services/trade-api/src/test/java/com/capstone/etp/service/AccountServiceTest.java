package com.capstone.etp.service;

import com.capstone.etp.dto.AccountResponse;
import com.capstone.etp.dto.BalanceResponse;
import com.capstone.etp.dto.OrderHistoryEntry;
import com.capstone.etp.dto.PositionResponse;
import com.capstone.etp.enums.AccountStatus;
import com.capstone.etp.enums.OrderSide;
import com.capstone.etp.enums.OrderStatus;
import com.capstone.etp.exception.AccountNotFoundException;
import com.capstone.etp.mapper.AccountMapper;
import com.capstone.etp.security.AccountAuthorizer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @Mock
    private AccountMapper accountMapper;

    @Mock
    private AccountAuthorizer accountAuthorizer;

    private AccountService accountService;

    private static final Long ACCOUNT_ID = 1L;
    private static final String ACCOUNT_REF = "ACC-001";

    @BeforeEach
    void setUp() {
        accountService = new AccountService(accountMapper, accountAuthorizer);
    }

    //account details returned successfully
    @Test
    void getAccount_WithValidAccount_ReturnsAccountResponse() {

        AccountResponse expectedResponse = createAccountResponse();

        when(accountMapper.getAccountById(ACCOUNT_ID))
                .thenReturn(expectedResponse);

        AccountResponse result = accountService.getAccount(ACCOUNT_ID);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(ACCOUNT_ID);
        assertThat(result.getAccountId()).isEqualTo(ACCOUNT_REF);

        verify(accountMapper, times(2))
                .getAccountById(ACCOUNT_ID);
        verify(accountAuthorizer)
                .ensureAccountAccess(ACCOUNT_ID);
    }

    //balance returned successfully
    @Test
    void getBalance_WithValidAccount_ReturnsBalanceResponse() {

        AccountResponse accountResponse = createAccountResponse();
        BalanceResponse expectedResponse = createBalanceResponse();

        when(accountMapper.getAccountById(ACCOUNT_ID))
                .thenReturn(accountResponse);

        when(accountMapper.getBalance(ACCOUNT_ID))
                .thenReturn(expectedResponse);

        BalanceResponse result = accountService.getBalance(ACCOUNT_ID);

        assertThat(result).isNotNull();
        assertThat(result.getAccountId()).isEqualTo(ACCOUNT_ID);
        assertThat(result.getCashBalance())
                .isEqualTo(new BigDecimal("10000.00"));

        verify(accountMapper)
                .getAccountById(ACCOUNT_ID);

        verify(accountMapper)
                .getBalance(ACCOUNT_ID);
        verify(accountAuthorizer)
                .ensureAccountAccess(ACCOUNT_ID);
    }

    //positions returned successfully
    @Test
    void getPositions_WithValidAccount_ReturnsPositionsList() {

        AccountResponse accountResponse = createAccountResponse();

        List<PositionResponse> expectedPositions = List.of(
                createPositionResponse("INFY", 100),
                createPositionResponse("RELIANCE", 50)
        );

        when(accountMapper.getAccountById(ACCOUNT_ID))
                .thenReturn(accountResponse);

        when(accountMapper.getPositionsByAccountId(ACCOUNT_ID))
                .thenReturn(expectedPositions);

        List<PositionResponse> result =
                accountService.getPositions(ACCOUNT_ID);

        assertThat(result).isNotNull();
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getSymbol()).isEqualTo("INFY");
        assertThat(result.get(1).getSymbol()).isEqualTo("RELIANCE");

        verify(accountMapper)
                .getAccountById(ACCOUNT_ID);

        verify(accountMapper)
                .getPositionsByAccountId(ACCOUNT_ID);
        verify(accountAuthorizer)
                .ensureAccountAccess(ACCOUNT_ID);
    }

    //order history returned successfully
    @Test
    void getOrders_WithValidAccount_ReturnsOrderHistoryList() {

        AccountResponse accountResponse = createAccountResponse();

        OrderStatus filterStatus = OrderStatus.FILLED;

        Instant from = Instant.now().minusSeconds(30L * 24 * 60 * 60);
        Instant to = Instant.now();

        List<OrderHistoryEntry> expectedOrders = List.of(
                createOrderHistoryEntry(
                        UUID.fromString("550e8400-e29b-41d4-a716-446655440000"),
                        "INFY"
                ),
                createOrderHistoryEntry(
                        UUID.fromString("6ba7b810-9dad-41d1-80b4-00c04fd430c8"),
                        "RELIANCE"
                )
        );

        when(accountMapper.getAccountById(ACCOUNT_ID))
                .thenReturn(accountResponse);

        when(accountMapper.getOrdersByAccountId(
                ACCOUNT_ID,
                filterStatus,
                from,
                to
        )).thenReturn(expectedOrders);

        List<OrderHistoryEntry> result =
                accountService.getOrders(
                        ACCOUNT_ID,
                        filterStatus,
                        from,
                        to
                );

        assertThat(result).isNotNull();
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getOrderId()).isEqualTo(UUID.fromString("550e8400-e29b-41d4-a716-446655440000"));
        assertThat(result.get(1).getOrderId()).isEqualTo(UUID.fromString("6ba7b810-9dad-41d1-80b4-00c04fd430c8"));

        verify(accountMapper)
                .getAccountById(ACCOUNT_ID);

        verify(accountMapper)
                .getOrdersByAccountId(
                        ACCOUNT_ID,
                        filterStatus,
                        from,
                        to
                );
        verify(accountAuthorizer)
                .ensureAccountAccess(ACCOUNT_ID);
    }

    //unknown account returns ACC-404
    @Test
    void getAccount_WithUnknownAccountId_ThrowsAccountNotFoundException() {

        when(accountMapper.getAccountById(ACCOUNT_ID))
                .thenReturn(null);

        assertThatThrownBy(
                () -> accountService.getAccount(ACCOUNT_ID)
        )
                .isInstanceOf(AccountNotFoundException.class)
                .hasMessageContaining("Account not found");
        verify(accountAuthorizer)
                .ensureAccountAccess(ACCOUNT_ID);
    }

    //balance with unknown account returns ACC-404
    @Test
    void getBalance_WithUnknownAccountId_ThrowsAccountNotFoundException() {

        when(accountMapper.getAccountById(ACCOUNT_ID))
                .thenReturn(null);

        assertThatThrownBy(
                () -> accountService.getBalance(ACCOUNT_ID)
        )
                .isInstanceOf(AccountNotFoundException.class);
        verify(accountAuthorizer)
                .ensureAccountAccess(ACCOUNT_ID);
    }

    //positions with unknown account returns ACC-404
    @Test
    void getPositions_WithUnknownAccountId_ThrowsAccountNotFoundException() {

        when(accountMapper.getAccountById(ACCOUNT_ID))
                .thenReturn(null);

        assertThatThrownBy(
                () -> accountService.getPositions(ACCOUNT_ID)
        )
                .isInstanceOf(AccountNotFoundException.class);
        verify(accountAuthorizer)
                .ensureAccountAccess(ACCOUNT_ID);
    }

    // order history with unknown account returns ACC-404
    @Test
    void getOrders_WithUnknownAccountId_ThrowsAccountNotFoundException() {

        when(accountMapper.getAccountById(ACCOUNT_ID))
                .thenReturn(null);

        assertThatThrownBy(
                () -> accountService.getOrders(
                        ACCOUNT_ID,
                        null,
                        null,
                        null
                )
        )
                .isInstanceOf(AccountNotFoundException.class);
        verify(accountAuthorizer)
                .ensureAccountAccess(ACCOUNT_ID);
    }

    private AccountResponse createAccountResponse() {

        return AccountResponse.builder()
                .id(ACCOUNT_ID)
                .accountId(ACCOUNT_REF)
                .holderName("John Doe")
                .cashBalance(new BigDecimal("10000.00"))
                .status(AccountStatus.ACTIVE)
                .version(1)
                .lastUpdated(Instant.now())
                .build();
    }

    private BalanceResponse createBalanceResponse() {

        return BalanceResponse.builder()
                .accountId(ACCOUNT_ID)
                .cashBalance(new BigDecimal("10000.00"))
                .currency("INR")
                .asOf(Instant.now())
                .build();
    }

    private PositionResponse createPositionResponse(
            String symbol,
            int quantity) {

        return PositionResponse.builder()
                .accountId(ACCOUNT_ID)
                .symbol(symbol)
                .quantity(quantity)
                .averageCost(new BigDecimal("1500.00"))
                .build();
    }

    private OrderHistoryEntry createOrderHistoryEntry(
            UUID orderId,
            String symbol) {

        return OrderHistoryEntry.builder()
                .orderId(orderId)
                .accountId(ACCOUNT_ID)
                .symbol(symbol)
                .side(OrderSide.BUY)
                .quantity(100)
                .price(new BigDecimal("1500.00"))
                .status(OrderStatus.FILLED)
                .createdOn(Instant.now())
                .executedPrice(new BigDecimal("1500.00"))
                .build();
    }
}
