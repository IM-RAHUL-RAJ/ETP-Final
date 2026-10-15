package com.capstone.etp.exception;

public final class AccountNotActiveException extends ETPException {

    private final Long accountId;
    private final String currentStatus;

    public AccountNotActiveException(Long accountId, String currentStatus) {
        super("ACC-403", "Account not active");
        this.accountId = accountId;
        this.currentStatus = currentStatus;
    }

    public Long getAccountRef() {
        return accountId;
    }

    // CHANGE TO JAVA ENUM : For INVESTIGATION
    public String getCurrentStatus() {
        return currentStatus;
    }
}