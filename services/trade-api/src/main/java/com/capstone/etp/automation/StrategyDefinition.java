package com.capstone.etp.automation;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * One configured strategy, read from app.automation.strategies[n].*. It is off unless enabled is set, and it
 * is checked by StrategyDefinitionValidator before it is ever used.
 */
@Getter
@Setter
public class StrategyDefinition {

    private String id;
    private StrategyType type;
    /** The account the orders are placed for. */
    private Long accountId;
    private String symbol;
    private boolean enabled = false;
    private Integer quantity;

    /** MA_CROSSOVER: the short and the long moving-average period. */
    private Integer shortPeriod;
    private Integer longPeriod;

    /** BOLLINGER_BANDS: the moving-average period and the standard-deviation multiplier. */
    private Integer period;
    private BigDecimal multiplier = BigDecimal.valueOf(2);
}
