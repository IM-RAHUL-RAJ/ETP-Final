package com.capstone.etp.preferences;

import java.time.Instant;
import java.util.List;

//A customer's preferences as the settings screen shows them.
public record PreferencesResponse(
        Long accountId,
        Long defaultAccountId,
        AlertChannel alertChannel,
        String contactDetail,
        ChannelSource source,
        List<ChannelOption> channels,
        Instant updatedAt,
        Mascot mascot,
        boolean notificationsEnabled,
        List<String> notificationKinds) {

    public PreferencesResponse(Long accountId, Long defaultAccountId, AlertChannel alertChannel, String contactDetail,
                               ChannelSource source, List<ChannelOption> channels, Instant updatedAt) {
        this(accountId, defaultAccountId, alertChannel, contactDetail, source, channels, updatedAt,
                Mascot.OWL, true, PreferencesService.ALL_KINDS);
    }

    /** One channel the customer could pick, and whether the profile can serve it. */
    public record ChannelOption(AlertChannel channel, boolean available, String hint) {
    }
}
