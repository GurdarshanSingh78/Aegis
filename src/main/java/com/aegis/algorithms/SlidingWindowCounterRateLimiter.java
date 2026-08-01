package com.aegis.algorithms;

import com.aegis.core.RateLimiter;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Approximate sliding-window-counter limiter: fixed windows are indexed by
 * {@code now / windowNanos}, and the estimated count for "the last
 * {@code windowNanos}" is a weighted blend of the current window's count and
 * the previous window's count:
 *
 * <pre>
 * estimate = currentWindowCount + previousWindowCount * (1 - elapsedFractionOfCurrentWindow)
 * </pre>
 *
 * <p>This is the standard middle ground between the other two algorithms:
 * O(1) memory like the token bucket (only ever two live buckets), but far
 * smoother across window boundaries than a naive fixed-window counter,
 * because a request arriving just after a window rolls over is still
 * partially "charged" against the previous window's traffic. It is an
 * approximation, not an exact count -- under adversarial timing the true
 * rate in some sliding sub-interval can exceed the configured limit by a
 * bounded amount, which is the trade-off for O(1) memory.
 *
 * <h2>Concurrency design</h2>
 * Each fixed window's count lives in its own {@link AtomicLong}, stored in a
 * {@link ConcurrentHashMap} keyed by window index. {@code computeIfAbsent}
 * handles the race of two threads creating the same new window's counter for
 * the first time. The actual check-and-increment for the current window is a
 * CAS retry loop, so no thread ever blocks another; stale windows (anything
 * older than "previous") are opportunistically evicted after a successful
 * acquire to keep the map from growing without bound.
 */
public final class SlidingWindowCounterRateLimiter implements RateLimiter {

    private final long limit;
    private final long windowNanos;
    private final ConcurrentHashMap<Long, AtomicLong> windowCounts = new ConcurrentHashMap<>();

    /**
     * @param limit  maximum requests allowed per window
     * @param window length of each fixed window used in the weighted estimate
     */
    public SlidingWindowCounterRateLimiter(long limit, Duration window) {
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
        long currentIndex = Math.floorDiv(now, windowNanos);
        long previousIndex = currentIndex - 1;

        long previousCount = countOf(previousIndex);
        double elapsedIntoCurrent = now - currentIndex * windowNanos;
        double elapsedFraction = elapsedIntoCurrent / (double) windowNanos;
        double weightedPrevious = previousCount * (1.0 - elapsedFraction);

        AtomicLong currentBucket = windowCounts.computeIfAbsent(currentIndex, k -> new AtomicLong(0));

        while (true) {
            long currentCount = currentBucket.get();
            double estimated = weightedPrevious + currentCount;
            if (estimated + permits > limit) {
                return false;
            }
            if (currentBucket.compareAndSet(currentCount, currentCount + permits)) {
                evictStale(currentIndex);
                return true;
            }
            // Lost the race with another acquirer on this same window; retry
            // with a fresh read rather than blocking.
        }
    }

    private long countOf(long index) {
        AtomicLong bucket = windowCounts.get(index);
        return bucket == null ? 0L : bucket.get();
    }

    private void evictStale(long currentIndex) {
        windowCounts.keySet().removeIf(idx -> idx < currentIndex - 1);
    }
}
