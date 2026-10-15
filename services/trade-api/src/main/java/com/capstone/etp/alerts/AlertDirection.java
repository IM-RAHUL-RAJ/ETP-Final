package com.capstone.etp.alerts;

import java.math.BigDecimal;


public enum AlertDirection {
    ABOVE,
    BELOW;

    public boolean isCrossedBy(BigDecimal price, BigDecimal threshold) {
        int comparison = price.compareTo(threshold);
        return this == ABOVE ? comparison >= 0 : comparison <= 0;
    }
}
