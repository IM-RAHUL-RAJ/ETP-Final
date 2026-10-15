package com.capstone.etp.exception;

public final class CurrencyMismatchException extends ETPException {

    private final String detail;

    public CurrencyMismatchException(String detail) {
        super("CUR-422", "Currency mapping validation failed");
        this.detail = detail;
    }

    @Override
    public String getMessage() {
        return super.getMessage() + ": " + detail;
    }

    public String getDetail() {
        return detail;
    }
}
