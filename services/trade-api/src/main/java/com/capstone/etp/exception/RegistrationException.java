package com.capstone.etp.exception;

import org.springframework.http.HttpStatus;

/** A registration that cannot go ahead, with the HTTP status the caller should see. */
public final class RegistrationException extends ETPException {

    private final HttpStatus status;

    public RegistrationException(String code, HttpStatus status, String message) {
        super(code, message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
