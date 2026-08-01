package com.aegis.core;

/**
 * Signals that a call was rejected because the configured {@link RateLimiter}
 * had no permits available. Handed to the caller's fallback function rather
 * than thrown up the stack, so a rejected call never crashes the caller --
 * it degrades to whatever fallback behavior they've defined.
 */
public class RateLimitExceededException extends RuntimeException {

    public RateLimitExceededException(String message) {
        super(message);
    }
}
