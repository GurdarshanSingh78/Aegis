package com.aegis.algorithms;

import com.aegis.core.RateLimiter;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Exact sliding-window-log limiter: keeps a timestamp for every request
 * currently inside the trailing window and allows a new one only if fewer
 * than {@code limit} timestamps remain after purging expired entries.
 *
 * <h2>Concurrency design</h2>
 * Unlike the token bucket, "purge expired, then check count, then record"
 * is a three-step sequence that has to happen atomically -- two threads
 * both reading a count of {@code limit - 1} and both deciding to proceed
 * would let {@code limit + 1} requests through. That check-then-act
 * pattern can't be expressed as a single CAS, so this class uses a small
 * {@code synchronized} block around exactly those three steps instead of
 * forcing an awkward lock-free structure onto a problem that isn't a good
 * fit for one. The lock is held only for a purge-and-append over an
 * in-memory deque (no I/O, no blocking calls), so contention cost stays low
 * even though it's a real lock.
 *
 * <p>The cost of being exact is memory: this holds up to {@code limit}
 * {@code Long} timestamps at any moment, versus O(1) for the other two
 * strategies.
 */
public final class SlidingWindowLogRateLimiter implements RateLimiter {

    private final int limit;
    private final long windowNanos;
    private final Deque<Long> timestamps = new ArrayDeque<>();
    private final Object lock = new Object();

    /**
     * @param limit  maximum number of requests allowed inside any window
     * @param window length of the trailing window
     */
    public SlidingWindowLogRateLimiter(int limit, Duration window) {
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be positive, got " + limit);
        }
        if (window.isZero() || window.isNegative()) {
            throw new IllegalArgumentException("window must be positive, got " + window);
        }
        this.limit = limit;
        this.windowNanos = window.toNanos();
    }

    @Override
    public boolean tryAcquire() {
        return tryAcquire(1);
    }

    @Override
    public boolean tryAcquire(int permits) {
        if (permits <= 0) {
            throw new IllegalArgumentException("permits must be positive, got " + permits);
        }
        long now = System.nanoTime();
        long cutoff = now - windowNanos;

        synchronized (lock) {
            while (!timestamps.isEmpty() && timestamps.peekFirst() <= cutoff) {
                timestamps.pollFirst();
            }
            if (timestamps.size() + permits > limit) {
                return false;
            }
            for (int i = 0; i < permits; i++) {
                timestamps.addLast(now);
            }
            return true;
        }
    }

    /** Current number of requests counted inside the window, for metrics/diagnostics. */
    public int currentCount() {
        long cutoff = System.nanoTime() - windowNanos;
        synchronized (lock) {
            while (!timestamps.isEmpty() && timestamps.peekFirst() <= cutoff) {
                timestamps.pollFirst();
            }
            return timestamps.size();
        }
    }
}
