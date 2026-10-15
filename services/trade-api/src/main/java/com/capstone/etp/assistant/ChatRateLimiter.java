package com.capstone.etp.assistant;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** A sliding one-minute window per account, so a loop or a bot cannot run up the model bill. In memory, per instance. */
public class ChatRateLimiter {

    private final int perMinute;
    private final Clock clock;
    private final Map<Long, ArrayDeque<Instant>> windows = new ConcurrentHashMap<>();

    public ChatRateLimiter(int perMinute, Clock clock) {
        this.perMinute = Math.max(1, perMinute);
        this.clock = clock;
    }

    /** @return true if this question is within the limit (and counts against it) */
    public boolean tryAcquire(Long accountId) {
        Instant now = clock.instant();
        ArrayDeque<Instant> window = windows.computeIfAbsent(accountId, key -> new ArrayDeque<>());
        synchronized (window) {
            while (!window.isEmpty() && window.peekFirst().isBefore(now.minus(Duration.ofMinutes(1)))) {
                window.pollFirst();
            }
            if (window.size() >= perMinute) {
                return false;
            }
            window.addLast(now);
            return true;
        }
    }
}
