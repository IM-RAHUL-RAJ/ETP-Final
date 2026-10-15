package com.capstone.etp.notifications;

/**
 * Delivery state, tracked separately from the Kafka offset.
 *
 * The offset says a message was READ. This says whether it REACHED the customer. The offset is
 * committed as soon as the notification is durably recorded as QUEUED, so a provider having a bad
 * afternoon never stalls the partition.
 */
public enum NotificationStatus {
    /** Recorded and waiting to be sent (or to be sent again after a failed attempt). */
    QUEUED,
    /** The channel could not be resolved to a usable destination; kept and looked at again later. */
    HELD,
    /** Handed to the channel. */
    SENT,
    /** Gave up after the attempt limit. */
    FAILED,
    /** The customer switched this kind of message off. It stays in the inbox and is not sent. */
    SKIPPED
}
