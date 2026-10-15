package com.capstone.etp.exception;

public final class InsufficientHoldingsException extends ETPException {

    private final Long accountId;
    private final String symbol;
    private final Long requestedQuantity;
    private final Long availableQuantity;

    public InsufficientHoldingsException(Long accountId, String symbol,
            Long requestedQuantity, Long availableQuantity) {
        super("ORD-409", "Insufficient holdings");
        this.accountId = accountId;
        this.symbol = symbol;
        this.requestedQuantity = requestedQuantity;
        this.availableQuantity = availableQuantity;
    }

    public Long getaccountId() {
        return accountId;
    }

    public String getSymbol() {
        return symbol;
    }

    public Long getRequestedQuantity() {
        return requestedQuantity;
    }

    public Long getAvailableQuantity() {
        return availableQuantity;
    }
}