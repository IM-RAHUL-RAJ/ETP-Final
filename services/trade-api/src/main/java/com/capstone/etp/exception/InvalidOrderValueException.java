package com.capstone.etp.exception;

public final class InvalidOrderValueException extends ETPException {

    public InvalidOrderValueException(String message) {

        super("VAL-422", message);
    }

}