package com.capstone.etp.alerts;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;

/**
 * How a price is compared with the customer's level. Written as a symbol on the wire (">=") and
 * stored by name (GTE). An equality alert compares at the four decimal places the level is stored at.
 */
public enum AlertOperator {
    GT(">", AlertDirection.ABOVE),
    GTE(">=", AlertDirection.ABOVE),
    LT("<", AlertDirection.BELOW),
    LTE("<=", AlertDirection.BELOW),
    EQ("==", AlertDirection.ABOVE);

    private final String symbol;
    private final AlertDirection direction;

    AlertOperator(String symbol, AlertDirection direction) {
        this.symbol = symbol;
        this.direction = direction;
    }

    @JsonValue
    public String symbol() {
        return symbol;
    }

    /** The legacy direction column keeps a value that means something for every operator. */
    public AlertDirection direction() {
        return direction;
    }

    /** True when {@code price} satisfies "price {operator} threshold". */
    public boolean matches(BigDecimal price, BigDecimal threshold) {
        int comparison = price.setScale(4, RoundingMode.HALF_UP).compareTo(threshold.setScale(4, RoundingMode.HALF_UP));
        return switch (this) {
            case GT -> comparison > 0;
            case GTE -> comparison >= 0;
            case LT -> comparison < 0;
            case LTE -> comparison <= 0;
            case EQ -> comparison == 0;
        };
    }

    @JsonCreator
    public static AlertOperator from(String text) {
        if (text == null) {
            return null;
        }
        String value = text.trim();
        for (AlertOperator operator : values()) {
            if (operator.symbol.equals(value) || operator.name().equals(value.toUpperCase(Locale.ROOT))) {
                return operator;
            }
        }
        if ("=".equals(value)) {
            return EQ;
        }
        throw new IllegalArgumentException("Unknown condition: " + value);
    }
}
