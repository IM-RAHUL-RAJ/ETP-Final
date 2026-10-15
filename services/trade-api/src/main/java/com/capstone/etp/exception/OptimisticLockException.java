package com.capstone.etp.exception;

public final class OptimisticLockException extends ETPException {

    public OptimisticLockException(String message) {
        super("ORD-409", message);
    }
}
