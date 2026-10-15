package com.capstone.etp.validator;

import com.capstone.etp.entity.Account;
import com.capstone.etp.entity.Instrument;
import com.capstone.etp.entity.Order;
import com.capstone.etp.entity.PortfolioHolding;
import com.capstone.etp.enums.AccountType;
import com.capstone.etp.enums.AssetClass;
import com.capstone.etp.enums.OrderSide;
import com.capstone.etp.exception.*;
import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;


import org.springframework.stereotype.Service;

@Service 
public class OrderValidator {


    public void validate(
            Order order,
            Account account,
            Instrument instrument,
            PortfolioHolding holding,
            List<Order> existingOrders) {

        checkAccountExists(order, account);

        checkAccountActive(account);

        checkInstrument(instrument);

        //checkAccountTypeAndAssetClassCompatibility(account, instrument);

        //checkForexCryptoCurrencyCompatibility(account, instrument);

        checkQuantity(order);

        checkPrice(order);

        if (order.getSide() == OrderSide.BUY) {
            checkFunds(order, account);
        }

        if (order.getSide() == OrderSide.SELL) {
            checkHoldings(order, account, instrument, holding);
        }


        checkIdempotencyKey(order, existingOrders);
    }


    // Rule 1
    private void checkAccountExists(
            Order order,
            Account account) {

        if (account == null) {
            throw new AccountNotFoundException(
                    order.getAccountId());
        }
    }


    // Rule 2
    private void checkAccountActive(Account account) {

        if (!account.isActive()) {

            throw new AccountNotActiveException(
                    account.getId(),
                    account.getStatus().name());
        }
    }


    // Rule 3
    private void checkInstrument(Instrument instrument) {

        if (instrument == null) {

            throw new InstrumentNotFoundException(
                    instrument.getSymbol(),
                    InstrumentNotFoundException.Reason.UNKNOWN_SYMBOL);
        }

        if (!instrument.isTradable()) {

            throw new InstrumentNotFoundException(
                    instrument.getSymbol(),
                    InstrumentNotFoundException.Reason.NOT_TRADABLE);
        }
    }


    // Rule 4
    private void checkAccountTypeAndAssetClassCompatibility(Account account, Instrument instrument) {
        AccountType accountType = account.getAccountType();
        AssetClass assetClass = instrument.getAssetClass();

        if (accountType == null || assetClass == null) {
            throw new CurrencyMismatchException("Account type or instrument asset class is missing");
        }

        boolean matches;
        if (accountType == AccountType.FOREX) {
            matches = assetClass == AssetClass.FX;
        } else {
            matches = accountType.name().equals(assetClass.name());
        }

        if (!matches) {
            throw new CurrencyMismatchException(
                    "accountType=" + accountType + " is incompatible with assetClass=" + assetClass);
        }
    }

    // Rule 5
    private void checkQuantity(Order order) {

        if (order.getQuantity() <= 0) {

            throw new InvalidOrderValueException(
                    "Invalid quantity");
        }
    }


    // Rule 6
    private void checkPrice(Order order) {

        if (order.getLimitPrice() == null ||
                order.getLimitPrice()
                        .compareTo(BigDecimal.ZERO) <= 0) {

            throw new InvalidOrderValueException(
                    "Invalid price");
        }
    }


    // Rule 7
    private void checkFunds(
            Order order,
            Account account) {

        BigDecimal requiredAmount =
                order.getLimitPrice()
                        .multiply(
                                BigDecimal.valueOf(
                                        order.getQuantity()));

        if (!account.canAfford(requiredAmount)) {

            throw new InsufficientFundsException(
                    account.getId(),
                    requiredAmount,
                    account.getCashBalance());
        }
    }


    // Rule 8
    private void checkHoldings(
            Order order,
            Account account,
            Instrument instrument,
            PortfolioHolding holding) {

        int heldQuantity = holding == null
                ? 0
                : holding.getQuantity();

        if (heldQuantity < order.getQuantity()) {

            throw new InsufficientHoldingsException(
                    account.getId(),
                    instrument.getSymbol(),
                    (long) order.getQuantity(),
                    (long) heldQuantity);
        }
    }


    // Rule 9
    private void checkForexCryptoCurrencyCompatibility(
            Account account,
            Instrument instrument) {

        AssetClass assetClass = instrument.getAssetClass();

        // Only validate FX and CRYPTO instruments
        if (assetClass != AssetClass.FX &&
                assetClass != AssetClass.CRYPTO) {
            return;
        }

        String symbol = instrument.getSymbol();

        String settlementCurrency;

        if (assetClass == AssetClass.FX) {

            // FX:<BASE><QUOTE>
            // Example:
            // FX:EURUSD -> USD
            settlementCurrency = extractQuoteCurrency(symbol);

        } else {

            // CRYPTO format:
            // X:BTC-USD -> USD
            settlementCurrency = extractCryptoCurrency(symbol);
        }


        String accountCurrency =
                normalizeCurrency(account.getCurrency());


        if (!settlementCurrency.equals(accountCurrency)) {

            throw new CurrencyMismatchException(
                    "Instrument " + symbol
                            + " settles in " + settlementCurrency
                            + " but account currency is "
                            + accountCurrency
            );
        }
    }


    // Extract quote currency from FX:<BASE><QUOTE>
    // Example:
    // FX:EURUSD -> USD
    // FX:USDJPY -> JPY
    private String extractQuoteCurrency(String symbol) {

        if (symbol == null ||
                !symbol.startsWith("FX:") ||
                symbol.length() != 9) {

            throw new CurrencyMismatchException(
                    "Invalid FX symbol format: " + symbol
            );
        }


        String pair =
                symbol.substring(3)
                        .toUpperCase(Locale.ROOT);


        return pair.substring(3, 6);
    }


    private String normalizeCurrency(String currency) {

        if (currency == null) {
            return "";
        }

        return currency
                .trim()
                .toUpperCase(Locale.ROOT);
    }

    private String extractCryptoCurrency(String symbol) {

        if (symbol == null ||
                !symbol.startsWith("X:") ||
                !symbol.contains("-")) {

            throw new CurrencyMismatchException(
                    "Invalid crypto symbol format: " + symbol
            );
        }


        String[] parts =
                symbol.substring(2)
                        .toUpperCase(Locale.ROOT)
                        .split("-");


        if (parts.length != 2) {

            throw new CurrencyMismatchException(
                    "Invalid crypto symbol format: " + symbol
            );
        }


        return parts[1];
    }

    // Rule 10
    private void checkIdempotencyKey(
            Order order,
            List<Order> existingOrders) {

        if (existingOrders == null) {
            return;
        }

        boolean duplicate = existingOrders
                .stream()
                .anyMatch(existing ->
                        order.getIdempotencyKey() != null &&
                                order.getIdempotencyKey()
                                        .equals(existing.getIdempotencyKey())
                );

        if (duplicate) {

            throw new DuplicateOrderException(
                    order.getIdempotencyKey()
            );
        }
    }
}