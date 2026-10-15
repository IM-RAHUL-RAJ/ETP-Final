package com.capstone.etp.executor.exception;


public class SymbolRequestException extends RuntimeException {

    private final String symbol;
    private final int statusCode;
    private final String body;

    public SymbolRequestException(String symbol, int statusCode, String body) {
        super("Fauxnance rejected symbol request: symbol=" + symbol + ", status=" + statusCode);
        this.symbol = symbol;
        this.statusCode = statusCode;
        this.body = body;
    }

    public String getSymbol() {
        return symbol;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getBody() {
        return body;
    }
}
