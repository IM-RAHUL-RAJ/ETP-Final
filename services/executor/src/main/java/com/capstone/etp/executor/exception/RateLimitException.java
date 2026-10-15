package com.capstone.etp.executor.exception;

//Exception thrown when Fauxnance rate limit is reached, indicating the caller should stop sending requests for the current poll cycle.
public class RateLimitException extends RuntimeException {

    private final Integer retryAfterSeconds;

    public RateLimitException(Integer retryAfterSeconds) {
        super("Fauxnance rate limit reached" +
                (retryAfterSeconds != null ? "; retry after " + retryAfterSeconds + "s" : ""));
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public Integer getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
