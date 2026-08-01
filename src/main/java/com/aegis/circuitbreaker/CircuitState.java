package com.aegis.circuitbreaker;

/**
 * The three states in the standard circuit breaker state machine.
 *
 * <pre>
 *      failures &gt;= threshold
 *   CLOSED ─────────────────────▶ OPEN
 *     ▲                             │
 *     │                             │ openTimeout elapses
 *     │ trial calls succeed         ▼
 *     └───────────────────────── HALF_OPEN
 *                any trial call fails
 *                       │
 *                       ▼
 *                     OPEN
 * </pre>
 */
public enum CircuitState {

    /** Normal operation: calls flow through to the downstream service. */
    CLOSED,

    /** Downstream considered unhealthy: calls are short-circuited to the fallback immediately. */
    OPEN,

    /**
     * Trial period after the open timeout expires: a limited number of
     * calls are let through to test whether downstream has recovered.
     */
    HALF_OPEN
}
