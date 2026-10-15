package com.capstone.etp.portfolio;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;


interface QuoteProvider {

    Map<String, ProviderQuote> fetch(List<String> symbols) throws QuoteProviderException;

    int MAX_BATCH = 25;

    /** previousClose is the last close before today's session, when the provider says. It is what "today" is measured from. */
    record ProviderQuote(String symbol, BigDecimal price, String currency, Instant asOf, boolean stale, BigDecimal previousClose) {
        ProviderQuote(String symbol, BigDecimal price, String currency, Instant asOf, boolean stale) {
            this(symbol, price, currency, asOf, stale, null);
        }
    }

    class QuoteProviderException extends Exception {
        QuoteProviderException(String message) {
            super(message);
        }

        QuoteProviderException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
