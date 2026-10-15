package com.capstone.etp.exception;

public final class AccountAccessDeniedException extends ETPException {

    public AccountAccessDeniedException() {
        super("ACC-403", "Sorry you dont have access to this account");
    }

    public AccountAccessDeniedException(String message) {
        super("ACC-403", message);
    }
}
