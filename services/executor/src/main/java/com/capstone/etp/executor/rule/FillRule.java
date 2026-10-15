package com.capstone.etp.executor.rule;

import com.capstone.etp.executor.entity.Order;
import com.capstone.etp.executor.enums.OrderSide;
import com.capstone.etp.executor.kafka.models.QuotePayload;

import java.math.BigDecimal;
import java.math.RoundingMode;


public final class FillRule {

    private FillRule() {
        // utility class
    }

    public static FillDecision apply(Order order, QuotePayload quote) {
        if (order.getSide() == OrderSide.BUY) {
            BigDecimal ask = quote.getAsk().setScale(2, RoundingMode.HALF_UP);
            if (order.getLimitPrice().compareTo(ask) >= 0) {
                return FillDecision.fill(ask);
            }
            return FillDecision.reject("PRICE_NOT_MET");
        } else {
            // SELL
            BigDecimal bid = quote.getBid().setScale(2, RoundingMode.HALF_UP);
            if (order.getLimitPrice().compareTo(bid) <= 0) {
                return FillDecision.fill(bid);
            }
            return FillDecision.reject("PRICE_NOT_MET");
        }
    }
}

