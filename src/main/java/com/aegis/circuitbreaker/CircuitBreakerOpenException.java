package com.aegis.circuitbreaker;

/**
 * Signals that a call was short-circuited because the breaker is currently
 * {@link CircuitState#OPEN} (or its half-open trial slots are full).
 * Passed to the caller's fallback function rather than propagated as a
 * crash -- the entire point of the breaker is to fail fast and gracefully
 * while downstream recovers.
 */
public class CircuitBreakerOpenException extends RuntimeException {

    public CircuitBreakerOpenException(String message) {
        super(message);
    }
}
