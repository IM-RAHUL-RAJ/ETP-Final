package com.capstone.etp.notifications;

/** The alert could not be recorded for delivery. The caller keeps the alert and tries again. */
public class AlertDeliveryException extends RuntimeException {

    public AlertDeliveryException(String message, Throwable cause) {
        super(message, cause);
    }

    public AlertDeliveryException(String message) {
        super(message);
    }
}
