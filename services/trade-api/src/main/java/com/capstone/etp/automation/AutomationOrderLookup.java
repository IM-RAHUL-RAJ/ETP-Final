package com.capstone.etp.automation;

import com.capstone.etp.notifications.AutomatedOrderLookup;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/** Tells the notifications whether an order came from a strategy, from the automation history. Never throws. */
@Slf4j
@Component
class AutomationOrderLookup implements AutomatedOrderLookup {

    private final AutomationEventMapper events;

    AutomationOrderLookup(AutomationEventMapper events) {
        this.events = events;
    }

    @Override
    public Optional<String> describe(UUID orderId) {
        if (orderId == null) {
            return Optional.empty();
        }
        try {
            AutomationEvent event = events.findForOrder(orderId);
            if (event == null) {
                return Optional.empty();
            }
            String rule = "BOLLINGER_BANDS".equals(event.getStrategyType()) ? "Bollinger bands" : "moving-average crossover";
            String number = event.getStrategyId() == null ? "" : " #" + event.getStrategyId();
            return Optional.of("your " + rule + " strategy" + number + " on " + event.getSymbol());
        } catch (RuntimeException e) {
            log.warn("[AUTOMATION] could not check whether order {} was automated: {}", orderId, e.getClass().getSimpleName());
            return Optional.empty();
        }
    }
}
