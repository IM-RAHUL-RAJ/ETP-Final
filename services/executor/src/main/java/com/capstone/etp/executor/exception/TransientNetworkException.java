package com.capstone.etp.executor.exception;


public class TransientNetworkException extends RuntimeException {

    private final String symbol;
    private final int attempts;

    public TransientNetworkException(String symbol, int attempts, Throwable cause) {
        super("Transient Fauxnance failure for symbol set '" + symbol + "' after " + attempts + " attempts", cause);
        this.symbol = symbol;
        this.attempts = attempts;
    }

    public String getSymbol() {
        return symbol;
    }

    public int getAttempts() {
        return attempts;
    }
}
