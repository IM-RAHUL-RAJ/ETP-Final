package com.capstone.etp.preferences;

// Signals that the preferences store is unavailable so the caller can retain the notification and retry.
public class PreferencesUnavailableException extends RuntimeException {

    public PreferencesUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}