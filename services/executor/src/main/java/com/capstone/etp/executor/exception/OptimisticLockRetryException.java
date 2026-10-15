package com.capstone.etp.executor.exception;

//Exception thrown when the account optimistic lock fails inside a transactional settlement, triggering a retry.
public class OptimisticLockRetryException extends RuntimeException {
    public OptimisticLockRetryException(String message) {
        super(message);
    }
}

