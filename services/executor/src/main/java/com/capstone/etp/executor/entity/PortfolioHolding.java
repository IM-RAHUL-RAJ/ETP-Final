package com.capstone.etp.executor.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PortfolioHolding {
    private Long portfolioId;
    private Long instrumentId;
    private int quantity;
    private BigDecimal avgPrice;
}

