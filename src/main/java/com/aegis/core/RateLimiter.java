package com.aegis.core;

/**
 * Strategy interface implemented by every rate-limiting algorithm
 * (token bucket, sliding-window-log, sliding-window-counter, ...).
 *
 * <p>Implementations MUST be thread-safe: {@link #tryAcquire()} and
 * {@link #tryAcquire(int)} will be called concurrently from many
 * request-handling threads at once. Callers never take out a lock
 * themselves; each strategy is responsible for its own internal
 * synchronization strategy (lock-free CAS loops, a narrow
 * {@code synchronized} block, or a {@link java.util.concurrent.ConcurrentHashMap}
 * bucket map, depending on what the algorithm needs).
 */
public interface RateLimiter {

    /**
     * Attempts to acquire a single permit.
     *
     * @return {@code true} if the request is allowed, {@code false} if it
     *         should be rejected (HTTP 429, or wherever this is wired in).
     */
    boolean tryAcquire();

    /**
     * Attempts to acquire {@code permits} permits atomically: either all of
     * them are granted, or none are and the caller should back off.
     *
     * @param permits number of permits requested, must be positive
     * @return {@code true} if all permits were granted
     */
    boolean tryAcquire(int permits);
}
