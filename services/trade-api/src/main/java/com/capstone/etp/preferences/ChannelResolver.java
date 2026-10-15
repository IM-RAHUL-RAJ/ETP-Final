package com.capstone.etp.preferences;

import java.util.Optional;

// Defines the in-process contract for resolving a customer's preferred notification channel, including defaults and failure handling.
public interface ChannelResolver {

    Optional<ResolvedChannel> resolve(long accountId);

    /**
     * Whether the customer wants this kind of message sent. Everything is wanted until they switch it off.
     * An unreadable preference must not silence a message, so an implementation answers true when unsure.
     */
    default boolean wants(long accountId, String kind) {
        return true;
    }

    /** The companion the customer chose, as OWL, RABBIT or CAT, for the artwork in e-mails. */
    default String mascotOf(long accountId) {
        return "OWL";
    }
}
