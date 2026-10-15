package com.capstone.etp.preferences;

import lombok.Data;

import java.time.Instant;

/** One account joined to its client and, if any, the client's stored preference. */
@Data
public class PreferenceRow {
    private Long clientId;
    private String email;
    private String phone;
    private AlertChannel alertChannel;
    private Long defaultAccountId;
    private Instant updatedAt;
    private Mascot mascot;
    private Boolean notificationsEnabled;
    private String notificationKinds;
}