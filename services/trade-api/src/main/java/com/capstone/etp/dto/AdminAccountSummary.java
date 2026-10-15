package com.capstone.etp.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/** One row of the admin's account list: the account joined to its client. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminAccountSummary {
    private Long accountId;
    private String accountRef;
    private String clientRef;
    private String clientName;
    private String email;
    private String phone;
    private String accountType;
    private String currency;
    private BigDecimal cashBalance;
    private String status;
    private Instant createdAt;
}
