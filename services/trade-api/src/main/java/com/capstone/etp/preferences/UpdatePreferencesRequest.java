package com.capstone.etp.preferences;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.List;

/**
 * The customer's choices. Mascot, the notification switch and the kinds are optional: what is absent stays as it was.
 */
public record UpdatePreferencesRequest(
        @NotNull @Positive Long defaultAccountId,
        @NotNull AlertChannel alertChannel,
        Mascot mascot,
        Boolean notificationsEnabled,
        List<String> notificationKinds) {

    public UpdatePreferencesRequest(Long defaultAccountId, AlertChannel alertChannel) {
        this(defaultAccountId, alertChannel, null, null, null);
    }

    boolean hasExtras() {
        return mascot != null || notificationsEnabled != null || notificationKinds != null;
    }
}
