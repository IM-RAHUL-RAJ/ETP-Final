package com.capstone.etp.executor.exception;
//Exception thrown when a message cannot succeed on any retry and is sent to the dead-letter topic.
public class DeadLetterException extends RuntimeException {
    public DeadLetterException(String reason) {
        super(reason);
    }
}

