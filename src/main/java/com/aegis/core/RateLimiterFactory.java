package com.aegis.core;

import com.aegis.algorithms.SlidingWindowCounterRateLimiter;
import com.aegis.algorithms.SlidingWindowLogRateLimiter;
import com.aegis.algorithms.TokenBucketRateLimiter;

/**
 * Builds the concrete {@link RateLimiter} implementation named by a
 * {@link RateLimiterConfig}. Keeping this switch in one place means callers
 * (and {@code AegisGateway.Builder}) only ever depend on the
 * {@link RateLimiter} interface and {@link RateLimiterAlgorithm} enum --
 * never on a concrete algorithm class -- which is what makes swapping
 * algorithms a one-line config change instead of a code change.
 */
public final class RateLimiterFactory {

    private RateLimiterFactory() {
        // static factory, not instantiable
    }

    public static RateLimiter create(RateLimiterConfig config) {
        return switch (config.getAlgorithm()) {
            case TOKEN_BUCKET -> new TokenBucketRateLimiter(
                    config.getCapacity(), config.getRefillRatePerSecond());
            case SLIDING_WINDOW_LOG -> new SlidingWindowLogRateLimiter(
                    (int) config.getCapacity(), config.getWindowSize());
            case SLIDING_WINDOW_COUNTER -> new SlidingWindowCounterRateLimiter(
                    config.getCapacity(), config.getWindowSize());
        };
    }
}
