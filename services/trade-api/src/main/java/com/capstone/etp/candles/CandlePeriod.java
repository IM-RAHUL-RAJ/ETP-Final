package com.capstone.etp.candles;

import java.util.Locale;

/** The bar sizes the chart offers. */
public enum CandlePeriod {
    ONE_DAY("1d"),
    FOUR_HOUR("4h"),
    ONE_HOUR("1h"),
    FIFTEEN_MIN("15m");

    private final String code;

    CandlePeriod(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public static CandlePeriod parse(String text) {
        String value = text == null ? "1d" : text.trim().toLowerCase(Locale.ROOT);
        for (CandlePeriod period : values()) {
            if (period.code.equals(value)) {
                return period;
            }
        }
        return null;
    }
}
