package com.capstone.etp.exception;

public final class InstrumentNotFoundException extends ETPException {

    public enum Reason {
        UNKNOWN_SYMBOL, NOT_TRADABLE
    }

    private final String symbol;
    private final Reason reason;

    public InstrumentNotFoundException(String symbol, Reason reason) {
        super("INS-404", "Instrument not found: "+reason.toString());
        this.symbol = symbol;
        this.reason = reason;
    }

    public String getSymbol() {
        return symbol;
    }

    public Reason getReason() {
        return reason;
    }
}