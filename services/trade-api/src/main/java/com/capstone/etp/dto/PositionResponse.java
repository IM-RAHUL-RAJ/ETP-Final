package com.capstone.etp.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PositionResponse {
    private Long accountId;
    private String symbol;
    private Integer quantity;
    private BigDecimal averageCost;
}
