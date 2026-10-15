package com.capstone.etp.exception;

import org.springframework.http.HttpStatus;

//A refusal raised by one of the extension modules (preferences, notifications, alerts, portfolio).

public class ModuleException extends ETPException {

    private final HttpStatus status;

    public ModuleException(HttpStatus status, String catalogueCode, String message) {
        super(catalogueCode, message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}