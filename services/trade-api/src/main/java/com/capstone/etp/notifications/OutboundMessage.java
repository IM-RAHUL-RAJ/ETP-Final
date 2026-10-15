package com.capstone.etp.notifications;

/**
 * One message on its way out. {@code destination} is personal data, so {@link #toString()} masks it
 * and the subject/body are not printed at all.
 */
record OutboundMessage(String destination, String subject, String body, NotificationKind kind, String mascot) {

    OutboundMessage(String destination, String subject, String body) {
        this(destination, subject, body, null, null);
    }

    @Override
    public String toString() {
        return "OutboundMessage[destination=" + masked() + "]";
    }

    /** "p***@example.com", "******3210", "account:7": enough to recognise, not enough to misuse. */
    String masked() {
        if (destination == null || destination.isBlank()) {
            return "<none>";
        }
        int at = destination.indexOf('@');
        if (at > 0) {
            return destination.charAt(0) + "***" + destination.substring(at);
        }
        if (destination.startsWith("account:")) {
            return destination;
        }
        return destination.length() <= 4
                ? "****"
                : "*".repeat(destination.length() - 4) + destination.substring(destination.length() - 4);
    }
}
