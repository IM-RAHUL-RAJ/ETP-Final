package com.capstone.etp.exception;

public final class IllegalOrderStateException extends ETPException {

    public IllegalOrderStateException(String message) {
        super("ORD-409", message);
    }
}
