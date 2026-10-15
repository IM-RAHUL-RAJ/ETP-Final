package com.capstone.etp.notifications;

import com.capstone.etp.kafka.MalformedEventException;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;


@Component
class NotificationComposer {

    record Draft(NotificationKind kind, String subject, String body) {
    }

    private final AutomatedOrderLookup automated;

    NotificationComposer() {
        this((AutomatedOrderLookup) orderId -> java.util.Optional.empty());
    }

    @org.springframework.beans.factory.annotation.Autowired
    NotificationComposer(org.springframework.beans.factory.ObjectProvider<AutomatedOrderLookup> automated) {
        this(automated.getIfAvailable(() -> orderId -> java.util.Optional.empty()));
    }

    NotificationComposer(AutomatedOrderLookup automated) {
        this.automated = automated;
    }

    Draft compose(String eventType, TradeEvent event) {
        Draft draft = plain(eventType, event);
        java.util.Optional<String> strategy = automated.describe(event.orderId());
        if (strategy.isEmpty()) {
            return draft;
        }
        // Explicit that no person pressed the button: the subject is marked and the message names the strategy.
        return new Draft(draft.kind(), EmailTemplateRenderer.AUTOMATED_PREFIX + Character.toLowerCase(draft.subject().charAt(0)) + draft.subject().substring(1),
                "This trade was placed automatically by " + strategy.get() + ". " + draft.body());
    }

    private Draft plain(String eventType, TradeEvent event) {
        String order = event.side() + " " + event.quantity() + " " + event.symbol();
        return switch (eventType) {
            case "ORDER_FILLED" -> new Draft(NotificationKind.ORDER_FILLED,
                    "Order filled: " + order,
                    "Your " + event.side() + " order for " + event.quantity() + " " + event.symbol()
                            + " was filled at " + money(event.executedPrice())
                            + (event.price() != null ? " (your limit was " + money(event.price()) + ")" : "")
                            + ".");
            case "ORDER_REJECTED" -> new Draft(NotificationKind.ORDER_REJECTED,
                    "Order rejected: " + order,
                    "Your " + event.side() + " order for " + event.quantity() + " " + event.symbol()
                            + " was not carried out because " + rejectionReason(event.reason()) + ".");
            case "ORDER_CANCELLED" -> new Draft(NotificationKind.ORDER_CANCELLED,
                    "Order cancelled: " + order,
                    "Your " + event.side() + " order for " + event.quantity() + " " + event.symbol()
                            + " was cancelled" + ("CANCELLED_BY_CUSTOMER".equals(event.reason())
                            ? " at your request." : "."));
            default -> throw new MalformedEventException("Unsupported event type");
        };
    }

    /** A reason code becomes a sentence; a code nobody has mapped becomes a pointer to the blotter, never the raw code. */
    static String rejectionReason(String code) {
        if (code == null) {
            return "it could not be completed. See your order history for details";
        }
        return switch (code) {
            case "INSUFFICIENT_FUNDS" -> "there was not enough cash in your account";
            case "INSUFFICIENT_HOLDINGS" -> "you do not hold enough of this instrument to sell that quantity";
            case "PRICE_NOT_MET" -> "the market price did not meet your limit price";
            case "INSTRUMENT_NOT_TRADABLE" -> "the instrument is not tradable right now";
            case "ACCOUNT_NOT_ACTIVE" -> "your account is not active";
            default -> "it could not be completed. See your order history for details";
        };
    }

    static String money(BigDecimal amount) {
        return amount == null ? "the market price" : amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
