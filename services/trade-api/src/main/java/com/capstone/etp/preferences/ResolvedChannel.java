package com.capstone.etp.preferences;

// Represents the resolved notification channel and destination while preventing sensitive contact data from being logged.
public record ResolvedChannel(AlertChannel channel, String destination, ChannelSource source) {

    @Override
    public String toString() {
        return "ResolvedChannel[channel=" + channel + ", source=" + source + ", destination=<not logged>]";
    }
}