package com.capstone.etp.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BalanceResponse {
    private Long accountId;
    private BigDecimal cashBalance;
    private String currency;
    private Instant asOf;
}