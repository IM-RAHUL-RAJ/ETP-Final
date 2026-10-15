package com.capstone.etp.executor.entity;

import com.capstone.etp.executor.enums.AccountStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

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
    private String currency;
    private BigDecimal cashBalance;
    private AccountStatus status;
    private Long version;
    private Instant updatedAt;

    public boolean isActive() {
        return AccountStatus.ACTIVE == status;
    }

    public boolean canAfford(BigDecimal amount) {
        return cashBalance.compareTo(amount) >= 0;
    }
}

