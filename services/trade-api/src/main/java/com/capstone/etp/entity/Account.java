package com.capstone.etp.entity;

import com.capstone.etp.enums.AccountStatus;
import com.capstone.etp.enums.AccountType;
import lombok.*;
import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Account {

    private Long id;
    private String accountRef;
    private Long clientId;
    private AccountType accountType;
    private String currency;
    private BigDecimal cashBalance;
    private AccountStatus status;
    private Long version;
    private Instant createdAt;
    private Instant suspendedAt;
    private Instant closedAt;
    private Instant updatedAt;

    public boolean canAfford(BigDecimal amount) {
        return cashBalance.compareTo(amount) >= 0;
    }

    public void deposit(BigDecimal amount) {
        cashBalance = cashBalance.add(amount);
    }

    public void withdraw(BigDecimal amount) {
        if (!canAfford(amount)) {
            throw new IllegalStateException("Insufficient funds");
        }
        cashBalance = cashBalance.subtract(amount);
    }

    public void suspend() {
        if (this.status == AccountStatus.CLOSED) {
            throw new IllegalStateException(
                    "Closed account cannot be suspended");
        }
        if (this.status == AccountStatus.SUSPENDED) {
            throw new IllegalStateException(
                    "Account is already suspended");
        }
        this.status = AccountStatus.SUSPENDED;
        this.suspendedAt = Instant.now();
    }

    public void close() {
        if (this.status == AccountStatus.CLOSED) {
            throw new IllegalStateException(
                    "Account is already closed");
        }
        this.status = AccountStatus.CLOSED;
        this.closedAt = Instant.now();
    }

    public boolean isActive() {
        return status == AccountStatus.ACTIVE;
    }
}