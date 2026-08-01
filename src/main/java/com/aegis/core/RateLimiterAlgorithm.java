package com.aegis.core;

/**
 * The rate-limiting algorithms Aegis knows how to build. Passed to
 * {@link RateLimiterConfig.Builder} to pick a {@link RateLimiter}
 * implementation via {@link RateLimiterFactory} without the caller ever
 * naming a concrete class -- classic Strategy + Factory pairing.
 */
public enum RateLimiterAlgorithm {

    /**
     * Allows bursts up to a bucket capacity, then refills continuously at a
     * fixed rate. Cheap (one CAS loop, O(1) memory), tolerates bursts well,
     * but a client can spend a full bucket in a single instant.
     */
    TOKEN_BUCKET,

    /**
     * Keeps an exact timestamp log of every allowed request inside the
     * current window. Perfectly precise -- no boundary bursts -- at the
     * cost of O(limit) memory per limiter key and a purge pass per call.
     */
    SLIDING_WINDOW_LOG,

    /**
     * Approximates a sliding window using two adjacent fixed windows and a
     * weighted average between them. O(1) memory like token bucket, far
     * smoother at window boundaries, but it's an approximation rather than
     * an exact count.
     */
    SLIDING_WINDOW_COUNTER
}
