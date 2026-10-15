package com.capstone.etp.notifications;

import java.util.Collection;
import java.util.Map;

/**
 * What other modules may ask the notifications module: where a recorded message got to.
 * It answers for ids only; the caller has already authorised the account they came from.
 */
public interface NotificationStatusLookup {

    /** Notification id to its status name (QUEUED, HELD, SENT, FAILED). Unknown ids are absent. */
    Map<Long, String> statusOf(Collection<Long> notificationIds);
}
