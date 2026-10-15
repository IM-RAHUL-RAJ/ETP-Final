package com.capstone.etp.entity;

import com.capstone.etp.enums.AccountStatus;
import com.capstone.etp.enums.AccountType;
import com.capstone.etp.enums.ClientStatus;
import com.capstone.etp.enums.Gender;
import com.capstone.etp.enums.OrderSide;
import com.capstone.etp.enums.OrderStatus;
import com.capstone.etp.enums.RiskProfile;
import com.capstone.etp.enums.TransactionStatus;
import com.capstone.etp.exception.IllegalOrderStateException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EntityCoverageTest {

    @Test
    void order_terminalTransitionsAndGuard() {
        Order order = Order.builder().status(OrderStatus.NEW).build();
        order.fill();
        assertEquals(OrderStatus.FILLED, order.getStatus());
        assertNotNull(order.getTerminalAt());
        assertThrows(IllegalOrderStateException.class, order::cancel);

        Order cancelled = Order.builder().status(OrderStatus.NEW).build();
        cancelled.cancel();
        assertEquals(OrderStatus.CANCELLED, cancelled.getStatus());

        Order rejected = Order.builder().status(OrderStatus.NEW).build();
        rejected.reject();
        assertEquals(OrderStatus.REJECTED, rejected.getStatus());
    }

    @Test
    void account_financialAndLifecycleMethods() {
        Account account = Account.builder()
                .status(AccountStatus.ACTIVE)
                .accountType(AccountType.INDIAN_EQUITY)
                .cashBalance(new BigDecimal("100.00"))
                .build();

        assertTrue(account.canAfford(new BigDecimal("50.00")));
        account.deposit(new BigDecimal("25.00"));
        assertEquals(new BigDecimal("125.00"), account.getCashBalance());
        account.withdraw(new BigDecimal("20.00"));
        assertEquals(new BigDecimal("105.00"), account.getCashBalance());

        account.suspend();
        assertEquals(AccountStatus.SUSPENDED, account.getStatus());
        assertNotNull(account.getSuspendedAt());

        account.close();
        assertEquals(AccountStatus.CLOSED, account.getStatus());
        assertNotNull(account.getClosedAt());
        assertThrows(IllegalStateException.class, account::close);
    }

    @Test
    void portfolioHolding_updatesAveragePrice() {
        PortfolioHolding holding = PortfolioHolding.builder()
                .quantity(10)
                .avgPrice(new BigDecimal("100.00"))
                .build();

        holding.updateAveragePrice(10, new BigDecimal("110.00"));

        assertEquals(20, holding.getQuantity());
        assertEquals(new BigDecimal("105.00"), holding.getAvgPrice());
        assertThrows(IllegalArgumentException.class, () -> holding.updateAveragePrice(0, new BigDecimal("1")));
        assertThrows(IllegalArgumentException.class, () -> holding.updateAveragePrice(1, new BigDecimal("-1")));
    }

    @Test
    void plainEntities_gettersSettersAndBuilders() {
        Instrument instrument = Instrument.builder().symbol("AAPL").currency("USD").tradable(true).build();
        assertEquals("AAPL", instrument.getSymbol());

        Client client = Client.builder()
                .name("Jane")
                .email("jane@example.com")
                .dateOfBirth(LocalDate.of(1990, 1, 1))
                .gender(Gender.FEMALE)
                .status(ClientStatus.ACTIVE)
                .build();
        assertEquals("Jane", client.getName());

        AccountPreference preference = AccountPreference.builder()
                .accountId(10L)
                .riskProfile(RiskProfile.MODERATE)
                .preferredSectors(List.of("IT", "BFSI"))
                .createdAt(Instant.now())
                .build();
        assertEquals(10L, preference.getAccountId());

        Portfolio portfolio = Portfolio.builder().name("Core").currentValue(new BigDecimal("1000.00")).build();
        assertEquals("Core", portfolio.getName());

        Transaction transaction = Transaction.builder()
                .orderId(UUID.randomUUID())
                .quantity(5)
                .executedPrice(new BigDecimal("50.00"))
                .status(TransactionStatus.EXECUTED)
                .build();
        assertEquals(TransactionStatus.EXECUTED, transaction.getStatus());
    }
}
