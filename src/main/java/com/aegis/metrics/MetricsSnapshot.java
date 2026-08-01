package com.aegis.metrics;

import com.aegis.circuitbreaker.CircuitState;

/**
 * Point-in-time snapshot of gateway traffic, suitable for logging, testing,
 * or serializing straight onto an SSE stream for the live dashboard.
 *
 * @param allowed        requests that passed the rate limiter
 * @param rejected       requests turned away by the rate limiter (HTTP 429 territory)
 * @param success        downstream calls that completed successfully
 * @param failure        downstream calls that threw, counted against the breaker
 * @param shortCircuited calls that never reached downstream because the breaker was open
 * @param circuitState   circuit breaker state at the moment of the snapshot
 */
public record MetricsSnapshot(
        long allowed,
        long rejected,
        long success,
        long failure,
        long shortCircuited,
        CircuitState circuitState) {

    /** Hand-rolled JSON: the payload is small and fixed-shape, so a dependency isn't worth it. */
    public String toJson() {
        return "{"
                + "\"allowed\":" + allowed + ","
                + "\"rejected\":" + rejected + ","
                + "\"success\":" + success + ","
                + "\"failure\":" + failure + ","
                + "\"shortCircuited\":" + shortCircuited + ","
                + "\"circuitState\":\"" + circuitState + "\""
                + "}";
    }
}
