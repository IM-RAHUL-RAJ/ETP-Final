package com.capstone.etp.alerts;


public enum DeliveryState {
    // The alert has not fired. 
    NONE,
    // Fired; the hand-over to notifications has not been confirmed yet.
    PENDING_HANDOVER,
    // Notifications recorded it. From here on the notification's own status says whether it was sent.
    HANDED_OVER,
    // Notifications refused or could not record it, and the retries are used up or still going.
    HANDOVER_FAILED
}
