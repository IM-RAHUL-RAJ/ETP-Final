package com.capstone.etp.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

public final class CurrencyConverter {

    private static final Map<String, BigDecimal> USD_CONVERSION_RATES = Map.<String, BigDecimal>ofEntries(
            Map.entry("USD", new BigDecimal("1")),
            Map.entry("EUR", new BigDecimal("0.85")),
            Map.entry("GBP", new BigDecimal("0.75")),
            Map.entry("INR", new BigDecimal("95")),
            Map.entry("JPY", new BigDecimal("150")),
            Map.entry("CNY", new BigDecimal("7.20")),
            Map.entry("KRW", new BigDecimal("1400")),
            Map.entry("AUD", new BigDecimal("1.55")),
            Map.entry("CAD", new BigDecimal("1.38")),
            Map.entry("CHF", new BigDecimal("0.80")),
            Map.entry("HKD", new BigDecimal("7.80")),
            Map.entry("NZD", new BigDecimal("1.70")),
            Map.entry("SGD", new BigDecimal("1.30")),
            Map.entry("SEK", new BigDecimal("9.50")),
            Map.entry("NOK", new BigDecimal("10.00")),
            Map.entry("DKK", new BigDecimal("6.35")),
            Map.entry("PLN", new BigDecimal("3.65")),
            Map.entry("CZK", new BigDecimal("21.50")),
            Map.entry("HUF", new BigDecimal("340")),
            Map.entry("RON", new BigDecimal("4.35")),
            Map.entry("BGN", new BigDecimal("1.66")),
            Map.entry("ISK", new BigDecimal("125")),
            Map.entry("RSD", new BigDecimal("100")),
            Map.entry("TRY", new BigDecimal("41.00")),
            Map.entry("RUB", new BigDecimal("80.00")),
            Map.entry("UAH", new BigDecimal("41.50")),
            Map.entry("AED", new BigDecimal("3.6725")),
            Map.entry("SAR", new BigDecimal("3.75")),
            Map.entry("QAR", new BigDecimal("3.64")),
            Map.entry("KWD", new BigDecimal("0.307")),
            Map.entry("BHD", new BigDecimal("0.376")),
            Map.entry("OMR", new BigDecimal("0.385")),
            Map.entry("JOD", new BigDecimal("0.709")),
            Map.entry("ILS", new BigDecimal("3.40")),
            Map.entry("EGP", new BigDecimal("48.00")),
            Map.entry("ZAR", new BigDecimal("17.50")),
            Map.entry("NGN", new BigDecimal("1500")),
            Map.entry("KES", new BigDecimal("129")),
            Map.entry("GHS", new BigDecimal("15.00")),
            Map.entry("MAD", new BigDecimal("9.80")),
            Map.entry("TND", new BigDecimal("2.90")),
            Map.entry("DZD", new BigDecimal("132")),
            Map.entry("ETB", new BigDecimal("140")),
            Map.entry("TZS", new BigDecimal("2700")),
            Map.entry("UGX", new BigDecimal("3500")),
            Map.entry("RWF", new BigDecimal("1400")),
            Map.entry("MUR", new BigDecimal("45.00")),
            Map.entry("SCR", new BigDecimal("14.00")),
            Map.entry("BWP", new BigDecimal("13.50")),
            Map.entry("NAD", new BigDecimal("17.50")),
            Map.entry("ZMW", new BigDecimal("23.00")),
            Map.entry("BRL", new BigDecimal("5.50")),
            Map.entry("MXN", new BigDecimal("19.00")),
            Map.entry("ARS", new BigDecimal("1000")),
            Map.entry("CLP", new BigDecimal("950")),
            Map.entry("COP", new BigDecimal("4200")),
            Map.entry("PEN", new BigDecimal("3.55")),
            Map.entry("UYU", new BigDecimal("40.00")),
            Map.entry("BOB", new BigDecimal("6.90")),
            Map.entry("PYG", new BigDecimal("7800")),
            Map.entry("DOP", new BigDecimal("60.00")),
            Map.entry("CRC", new BigDecimal("510")),
            Map.entry("GTQ", new BigDecimal("7.70")),
            Map.entry("JMD", new BigDecimal("160")),
            Map.entry("BSD", new BigDecimal("1.00")),
            Map.entry("BBD", new BigDecimal("2.00")),
            Map.entry("TTD", new BigDecimal("6.80")),
            Map.entry("XCD", new BigDecimal("2.70"))
    );

    private CurrencyConverter() {
        // Utility class
    }

    public static BigDecimal convert(
            String fromCurrency,
            String toCurrency,
            BigDecimal amount
    ) {
        if (amount == null) {
            throw new IllegalArgumentException("Amount cannot be null");
        }

        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Amount cannot be negative");
        }

        String from = normalize(fromCurrency);
        String to = normalize(toCurrency);

        BigDecimal fromRate = getRate(from);
        BigDecimal toRate = getRate(to);

        BigDecimal amountInUsd = amount.divide(
                fromRate,
                10,
                RoundingMode.HALF_UP
        );

        return amountInUsd.multiply(toRate)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal getRate(String currency) {
        BigDecimal rate = USD_CONVERSION_RATES.get(currency);

        if (rate == null) {
            throw new IllegalArgumentException(
                    "Unsupported currency: " + currency
            );
        }

        return rate;
    }

    private static String normalize(String currency) {
        if (currency == null || currency.isBlank()) {
            throw new IllegalArgumentException("Currency cannot be null or blank");
        }

        return currency.trim().toUpperCase();
    }
}
