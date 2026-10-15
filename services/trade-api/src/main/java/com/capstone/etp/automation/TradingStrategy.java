package com.capstone.etp.automation;

import java.math.BigDecimal;
import java.util.List;

/**
 * One deterministic trading rule. It looks at the price history (oldest first, the newest price last) and says
 * whether the newest price is a new trigger. It holds no state: a trigger is a transition between the last two
 * observations, so the same condition holding on later ticks does not trigger again.
 */
public interface TradingStrategy {

    String name();

    /** How many observations the rule needs before it can say anything (it compares the last two). */
    int requiredObservations();

    StrategySignal evaluate(List<BigDecimal> prices);
}
