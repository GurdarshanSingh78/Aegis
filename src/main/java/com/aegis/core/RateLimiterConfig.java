package com.aegis.core;

import java.time.Duration;
import java.util.Objects;

/**
 * Immutable configuration for a {@link RateLimiter}, assembled with the
 * Builder pattern so callers get compile-time-friendly, readable
 * construction instead of a telescoping constructor:
 *
 * <pre>{@code
 * RateLimiterConfig config = RateLimiterConfig.builder(RateLimiterAlgorithm.TOKEN_BUCKET)
 *         .capacity(100)
 *         .refillRatePerSecond(20)
 *         .build();
 * }</pre>
 *
 * <p>Not every field is used by every algorithm -- {@code refillRatePerSecond}
 * only matters for {@link RateLimiterAlgorithm#TOKEN_BUCKET}, while
 * {@code windowSize} only matters for the two sliding-window strategies.
 * {@link RateLimiterFactory} validates the fields it actually needs for the
 * chosen algorithm and fails fast with a clear message otherwise.
 */
public final class RateLimiterConfig {

    private final RateLimiterAlgorithm algorithm;
    private final long capacity;
    private final double refillRatePerSecond;
    private final Duration windowSize;

    private RateLimiterConfig(Builder builder) {
        this.algorithm = builder.algorithm;
        this.capacity = builder.capacity;
        this.refillRatePerSecond = builder.refillRatePerSecond;
        this.windowSize = builder.windowSize;
    }

    public RateLimiterAlgorithm getAlgorithm() {
        return algorithm;
    }

    /** Bucket capacity (token bucket) or max requests per window (sliding window algorithms). */
    public long getCapacity() {
        return capacity;
    }

    /** Sustained throughput for {@link RateLimiterAlgorithm#TOKEN_BUCKET}, in tokens/second. */
    public double getRefillRatePerSecond() {
        return refillRatePerSecond;
    }

    /** Window length for the two sliding-window algorithms. */
    public Duration getWindowSize() {
        return windowSize;
    }

    public static Builder builder(RateLimiterAlgorithm algorithm) {
        return new Builder(algorithm);
    }

    public static final class Builder {
        private final RateLimiterAlgorithm algorithm;
        private long capacity = 100;
        private double refillRatePerSecond = 10;
        private Duration windowSize = Duration.ofSeconds(1);

        private Builder(RateLimiterAlgorithm algorithm) {
            this.algorithm = Objects.requireNonNull(algorithm, "algorithm must not be null");
        }

        /** Bucket capacity (token bucket) or requests allowed per window (sliding window). */
        public Builder capacity(long capacity) {
            if (capacity <= 0) {
                throw new IllegalArgumentException("capacity must be positive, got " + capacity);
            }
            this.capacity = capacity;
            return this;
        }

        /** Only relevant for {@link RateLimiterAlgorithm#TOKEN_BUCKET}. */
        public Builder refillRatePerSecond(double refillRatePerSecond) {
            if (refillRatePerSecond <= 0) {
                throw new IllegalArgumentException(
                        "refillRatePerSecond must be positive, got " + refillRatePerSecond);
            }
            this.refillRatePerSecond = refillRatePerSecond;
            return this;
        }

        /** Only relevant for the sliding-window algorithms. */
        public Builder windowSize(Duration windowSize) {
            Objects.requireNonNull(windowSize, "windowSize must not be null");
            if (windowSize.isZero() || windowSize.isNegative()) {
                throw new IllegalArgumentException("windowSize must be positive, got " + windowSize);
            }
            this.windowSize = windowSize;
            return this;
        }

        public RateLimiterConfig build() {
            return new RateLimiterConfig(this);
        }
    }
}
