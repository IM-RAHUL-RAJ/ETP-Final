package com.capstone.etp.notifications;

/**
 * What a notification is about. The first three come from {@code trade-events}; PRICE_ALERT from
 * price alerts; FUNDS from money added to or withdrawn from the account.
 */
public enum NotificationKind {
    ORDER_FILLED,
    ORDER_REJECTED,
    ORDER_CANCELLED,
    PRICE_ALERT,
    FUNDS;

    /** The groups the inbox can be filtered by. */
    public boolean isOrder() {
        return this == ORDER_FILLED || this == ORDER_REJECTED || this == ORDER_CANCELLED;
    }
}
