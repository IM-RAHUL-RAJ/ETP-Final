package com.capstone.etp.executor.rule;

import java.math.BigDecimal;


public final class FillDecision {

    private final boolean fill;
    private final BigDecimal executedPrice; // non-null only when fill == true
    private final String reason;            // non-null only when fill == false

    private FillDecision(boolean fill, BigDecimal executedPrice, String reason) {
        this.fill = fill;
        this.executedPrice = executedPrice;
        this.reason = reason;
    }

    public static FillDecision fill(BigDecimal executedPrice) {
        return new FillDecision(true, executedPrice, null);
    }

    public static FillDecision reject(String reason) {
        return new FillDecision(false, null, reason);
    }

    public boolean isFill() {
        return fill;
    }

    public BigDecimal getExecutedPrice() {
        return executedPrice;
    }

    public String getReason() {
        return reason;
    }
}

