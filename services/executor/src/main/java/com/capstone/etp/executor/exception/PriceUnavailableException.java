package com.capstone.etp.executor.exception;

//Exception thrown by FauxnanceClient when all retries are exhausted, indicating a price feed outage.
public class PriceUnavailableException extends RuntimeException {
    public PriceUnavailableException(String message) {
        super(message);
    }
}

