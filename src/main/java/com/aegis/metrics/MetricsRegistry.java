package com.aegis.metrics;

import com.aegis.circuitbreaker.CircuitState;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Plain atomic counters for everything the dashboard needs to show. Kept
 * deliberately separate from the rate limiter and circuit breaker
 * themselves -- they only need to decide allow/deny and open/closed, not
 * know anything about metrics export, keeping each class focused on one
 * responsibility (single responsibility principle).
 */
public final class MetricsRegistry {

    private final AtomicLong allowed = new AtomicLong();
    private final AtomicLong rejected = new AtomicLong();
    private final AtomicLong success = new AtomicLong();
    private final AtomicLong failure = new AtomicLong();
    private final AtomicLong shortCircuited = new AtomicLong();

    public void recordAllowed() {
        allowed.incrementAndGet();
    }

    public void recordRejected() {
        rejected.incrementAndGet();
    }

    public void recordSuccess() {
        success.incrementAndGet();
    }

    public void recordFailure() {
        failure.incrementAndGet();
    }

    public void recordShortCircuited() {
        shortCircuited.incrementAndGet();
    }

    public MetricsSnapshot snapshot(CircuitState circuitState) {
        return new MetricsSnapshot(
                allowed.get(),
                rejected.get(),
                success.get(),
                failure.get(),
                shortCircuited.get(),
                circuitState);
    }
}
