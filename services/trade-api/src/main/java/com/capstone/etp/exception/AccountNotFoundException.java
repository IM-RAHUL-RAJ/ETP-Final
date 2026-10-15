package com.capstone.etp.exception;

public final class AccountNotFoundException extends ETPException {

    private final Long accountId;

    public AccountNotFoundException(Long accountId) {
        super("ACC-404", "Account not found");
        this.accountId = accountId;
    }

    public Long getAccountId() {
        return accountId;
    }
}
