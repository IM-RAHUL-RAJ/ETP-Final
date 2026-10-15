package com.capstone.etp.dto;

import com.capstone.etp.enums.AccountStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.Instant;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AccountResponse {
    private Long id;                    //numeric internal key
    private String accountId;           //account ref
    private String holderName;
    private BigDecimal cashBalance;
    private AccountStatus status;
    private Integer version;
    private Instant lastUpdated;
}
