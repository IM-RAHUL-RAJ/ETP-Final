package com.capstone.etp.executor.exception;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExecutorExceptionsTest {

    @Test
    void deadLetterException_storesReason() {
        DeadLetterException ex = new DeadLetterException("DESERIALIZATION_FAILURE");
        assertEquals("DESERIALIZATION_FAILURE", ex.getMessage());
    }

    @Test
    void priceUnavailableException_storesMessage() {
        PriceUnavailableException ex = new PriceUnavailableException("empty quote");
        assertEquals("empty quote", ex.getMessage());
    }

    @Test
    void rateLimitException_exposesRetryAfterSeconds() {
        RateLimitException ex = new RateLimitException(7);
        assertEquals(7, ex.getRetryAfterSeconds());
        assertTrue(ex.getMessage().contains("retry after 7s"));
    }

    @Test
    void symbolRequestException_exposesFields() {
        SymbolRequestException ex = new SymbolRequestException("AAPL,MSFT", 400, "bad symbols");
        assertEquals("AAPL,MSFT", ex.getSymbol());
        assertEquals(400, ex.getStatusCode());
        assertEquals("bad symbols", ex.getBody());
    }

    @Test
    void transientNetworkException_exposesFieldsAndCause() {
        RuntimeException cause = new RuntimeException("timeout");
        TransientNetworkException ex = new TransientNetworkException("AAPL", 3, cause);
        assertEquals("AAPL", ex.getSymbol());
        assertEquals(3, ex.getAttempts());
        assertSame(cause, ex.getCause());
    }
}
