package com.capstone.etp.preferences;

/** Whether the channel is what the customer chose, or what the platform falls back to. */
public enum ChannelSource {
    /** The customer stored a preference. */
    STORED,
    /** Nothing has been stored, so the documented default applies (see {@link PreferencesService}). */
    DEFAULT
}