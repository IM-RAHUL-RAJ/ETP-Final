package com.capstone.etp.validator;

import com.capstone.etp.entity.Account;
import com.capstone.etp.entity.Instrument;
import com.capstone.etp.entity.Order;
import com.capstone.etp.entity.PortfolioHolding;
import com.capstone.etp.enums.AccountStatus;
import com.capstone.etp.enums.AccountType;
import com.capstone.etp.enums.AssetClass;
import com.capstone.etp.enums.OrderSide;
import com.capstone.etp.exception.DuplicateOrderException;
import com.capstone.etp.exception.AccountNotActiveException;
import com.capstone.etp.exception.AccountNotFoundException;
import com.capstone.etp.exception.InsufficientFundsException;
import com.capstone.etp.exception.InsufficientHoldingsException;
import com.capstone.etp.exception.InstrumentNotFoundException;
import com.capstone.etp.exception.InvalidOrderValueException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OrderValidatorTest {

    private final OrderValidator orderValidator = new OrderValidator();


    @Test
    void validate_allowsOrder_whenAccountTypeAndAssetClassDiffer_currencyChecksDisabled() {

        Order order = baseOrder(new BigDecimal("150.00"));

        Account account =
                baseAccount(
                        AccountType.US_EQUITY,
                        "USD",
                        "10000.00"
                );

        Instrument instrument =
                baseInstrument(
                        "RELIANCE.NS",
                        AssetClass.INDIAN_EQUITY,
                        "INR"
                );


        assertDoesNotThrow(
                () -> orderValidator.validate(
                        order,
                        account,
                        instrument,
                        null,
                        Collections.emptyList()
                )
        );
    }


    @Test
    void validate_acceptsForex_whenAccountCurrencyMatchesQuoteCurrency() {

        Order order =
                baseOrder(
                        new BigDecimal("1.20")
                );


        Account account =
                baseAccount(
                        AccountType.FOREX,
                        "USD",
                        "10000.00"
                );


        Instrument instrument =
                baseInstrument(
                        "FX:INRUSD",
                        AssetClass.FX,
                        "USD"
                );


        orderValidator.validate(
                order,
                account,
                instrument,
                null,
                Collections.emptyList()
        );


        // FX:INRUSD means 1 INR = X USD.
        // Validator only checks currency compatibility.
        // It does not convert price anymore.
        assertEquals(
                new BigDecimal("1.20"),
                order.getLimitPrice()
        );
    }


    @Test
    void validate_allowsForexWhenAccountCurrencyDoesNotMatchQuoteCurrency_currencyChecksDisabled() {

        Order order =
                baseOrder(
                        new BigDecimal("100.00")
                );


        Account account =
                baseAccount(
                        AccountType.FOREX,
                        "EUR",
                        "10000.00"
                );


        Instrument instrument =
                baseInstrument(
                        "FX:INRUSD",
                        AssetClass.FX,
                        "USD"
                );


        assertDoesNotThrow(
                () -> orderValidator.validate(
                        order,
                        account,
                        instrument,
                        null,
                        Collections.emptyList()
                )
        );
    }


    @Test
    void validate_keepsNonForexLimitPriceUnchanged() {

        Order order =
                baseOrder(
                        new BigDecimal("250.50")
                );


        Account account =
                baseAccount(
                        AccountType.US_EQUITY,
                        "USD",
                        "10000.00"
                );


        Instrument instrument =
                baseInstrument(
                        "AAPL",
                        AssetClass.US_EQUITY,
                        "USD"
                );


        orderValidator.validate(
                order,
                account,
                instrument,
                null,
                Collections.emptyList()
        );


        assertEquals(
                new BigDecimal("250.50"),
                order.getLimitPrice()
        );
    }

    @Test
    void validate_throwsInvalidOrderValue_whenQuantityIsNonPositive() {
        Order order = baseOrder(new BigDecimal("250.50"));
        order.setQuantity(0);

        InvalidOrderValueException exception = assertThrows(
                InvalidOrderValueException.class,
                () -> orderValidator.validate(
                        order,
                        baseAccount(AccountType.US_EQUITY, "USD", "10000"),
                        baseInstrument("AAPL", AssetClass.US_EQUITY, "USD"),
                        null,
                        Collections.emptyList()
                )
        );

        assertEquals("VAL-422", exception.getCatalogueCode());
    }

    @Test
    void validate_throwsInvalidOrderValue_whenPriceIsNullOrNegative() {
        Order nullPrice = baseOrder(new BigDecimal("1"));
        nullPrice.setLimitPrice(null);

        assertThrows(InvalidOrderValueException.class,
                () -> orderValidator.validate(
                        nullPrice,
                        baseAccount(AccountType.US_EQUITY, "USD", "10000"),
                        baseInstrument("AAPL", AssetClass.US_EQUITY, "USD"),
                        null,
                        Collections.emptyList()
                ));

        Order negativePrice = baseOrder(new BigDecimal("-1"));
        assertThrows(InvalidOrderValueException.class,
                () -> orderValidator.validate(
                        negativePrice,
                        baseAccount(AccountType.US_EQUITY, "USD", "10000"),
                        baseInstrument("AAPL", AssetClass.US_EQUITY, "USD"),
                        null,
                        Collections.emptyList()
                ));
    }

    @Test
    void validate_throwsInsufficientHoldings_forSellWhenHoldingTooLow() {
        Order sellOrder = baseOrder(new BigDecimal("250.50"));
        sellOrder.setSide(OrderSide.SELL);
        sellOrder.setQuantity(10);

        PortfolioHolding holding = PortfolioHolding.builder().quantity(5).build();

        assertThrows(InsufficientHoldingsException.class,
                () -> orderValidator.validate(
                        sellOrder,
                        baseAccount(AccountType.US_EQUITY, "USD", "10000"),
                        baseInstrument("AAPL", AssetClass.US_EQUITY, "USD"),
                        holding,
                        Collections.emptyList()
                ));
    }

    @Test
    void validate_throwsDuplicateOrder_forMatchingIdempotencyKey() {
        UUID sameKey = UUID.randomUUID();
        Order order = baseOrder(new BigDecimal("100"));
        order.setIdempotencyKey(sameKey);

        Order existing = baseOrder(new BigDecimal("90"));
        existing.setIdempotencyKey(sameKey);

        assertThrows(DuplicateOrderException.class,
                () -> orderValidator.validate(
                        order,
                        baseAccount(AccountType.US_EQUITY, "USD", "10000"),
                        baseInstrument("AAPL", AssetClass.US_EQUITY, "USD"),
                        null,
                        List.of(existing)
                ));
    }

    @Test
    void validate_allowsInvalidCryptoSymbolFormat_currencyChecksDisabled() {
        Order order = baseOrder(new BigDecimal("100"));

        assertDoesNotThrow(
                () -> orderValidator.validate(
                        order,
                        baseAccount(AccountType.CRYPTO, "USD", "10000"),
                        baseInstrument("X:BTCUSD", AssetClass.CRYPTO, "USD"),
                        null,
                        Collections.emptyList()
                ));
    }

    @Test
    void validate_throwsAccountNotFound_whenAccountMissing() {
        Order order = baseOrder(new BigDecimal("100"));
        assertThrows(AccountNotFoundException.class,
                () -> orderValidator.validate(
                        order,
                        null,
                        baseInstrument("AAPL", AssetClass.US_EQUITY, "USD"),
                        null,
                        Collections.emptyList()
                ));
    }

    @Test
    void validate_throwsAccountNotActive_whenStatusIsNotActive() {
        Order order = baseOrder(new BigDecimal("100"));
        Account account = baseAccount(AccountType.US_EQUITY, "USD", "10000");
        account.setStatus(AccountStatus.SUSPENDED);

        assertThrows(AccountNotActiveException.class,
                () -> orderValidator.validate(
                        order,
                        account,
                        baseInstrument("AAPL", AssetClass.US_EQUITY, "USD"),
                        null,
                        Collections.emptyList()
                ));
    }

    @Test
    void validate_throwsInstrumentNotFound_whenInstrumentNotTradable() {
        Order order = baseOrder(new BigDecimal("100"));
        Instrument instrument = baseInstrument("AAPL", AssetClass.US_EQUITY, "USD");
        instrument.setTradable(false);

        assertThrows(InstrumentNotFoundException.class,
                () -> orderValidator.validate(
                        order,
                        baseAccount(AccountType.US_EQUITY, "USD", "10000"),
                        instrument,
                        null,
                        Collections.emptyList()
                ));
    }

    @Test
    void validate_allowsMissingCompatibilityFields_currencyChecksDisabled() {
        Order order = baseOrder(new BigDecimal("100"));
        Account account = baseAccount(AccountType.US_EQUITY, "USD", "10000");
        account.setAccountType(null);

        assertDoesNotThrow(
                () -> orderValidator.validate(
                        order,
                        account,
                        baseInstrument("AAPL", AssetClass.US_EQUITY, "USD"),
                        null,
                        Collections.emptyList()
                ));
    }

    @Test
    void validate_throwsInsufficientFunds_forBuyWhenCashTooLow() {
        Order order = baseOrder(new BigDecimal("1000"));
        order.setQuantity(2);

        assertThrows(InsufficientFundsException.class,
                () -> orderValidator.validate(
                        order,
                        baseAccount(AccountType.US_EQUITY, "USD", "100"),
                        baseInstrument("AAPL", AssetClass.US_EQUITY, "USD"),
                        null,
                        Collections.emptyList()
                ));
    }

    @Test
    void validate_allowsSellWhenHoldingsSufficient_andNullExistingOrders() {
        Order order = baseOrder(new BigDecimal("100"));
        order.setSide(OrderSide.SELL);
        order.setQuantity(5);

        PortfolioHolding holding = PortfolioHolding.builder().quantity(5).build();

        assertDoesNotThrow(() -> orderValidator.validate(
                order,
                baseAccount(AccountType.US_EQUITY, "USD", "10000"),
                baseInstrument("AAPL", AssetClass.US_EQUITY, "USD"),
                holding,
                null
        ));
    }

    @Test
    void validate_ignoresDuplicateCheck_whenOrderIdempotencyKeyIsNull() {
        Order order = baseOrder(new BigDecimal("100"));
        order.setIdempotencyKey(null);
        Order existing = baseOrder(new BigDecimal("100"));

        assertDoesNotThrow(() -> orderValidator.validate(
                order,
                baseAccount(AccountType.US_EQUITY, "USD", "10000"),
                baseInstrument("AAPL", AssetClass.US_EQUITY, "USD"),
                null,
                List.of(existing)
        ));
    }


    private Order baseOrder(BigDecimal price) {

        return Order.builder()
                .id(UUID.randomUUID())
                .accountId(1L)
                .instrumentId(1L)
                .idempotencyKey(UUID.randomUUID())
                .side(OrderSide.BUY)
                .quantity(1)
                .limitPrice(price)
                .build();
    }


    private Account baseAccount(
            AccountType type,
            String currency,
            String balance) {

        return Account.builder()
                .id(1L)
                .accountType(type)
                .currency(currency)
                .cashBalance(new BigDecimal(balance))
                .status(AccountStatus.ACTIVE)
                .build();
    }


    private Instrument baseInstrument(
            String symbol,
            AssetClass assetClass,
            String currency) {

        return Instrument.builder()
                .id(1L)
                .symbol(symbol)
                .assetClass(assetClass)
                .currency(currency)
                .tradable(true)
                .build();
    }
}