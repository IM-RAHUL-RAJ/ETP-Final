package com.capstone.etp.exception;

import java.math.BigDecimal;

public final class InsufficientFundsException extends ETPException {

    private final Long accountId;
    private final BigDecimal requiredAmount;
    private final BigDecimal availableBalance;

    public InsufficientFundsException(Long accountId, BigDecimal requiredAmount, BigDecimal availableBalance) {
        super("ORD-400", "Insufficient funds");
        this.accountId = accountId;
        this.requiredAmount = requiredAmount;
        this.availableBalance = availableBalance;
    }

    public Long getaccountId() {
        return accountId;
    }

    public BigDecimal getRequiredAmount() {
        return requiredAmount;
    }

    public BigDecimal getAvailableBalance() {
        return availableBalance;
    }
}