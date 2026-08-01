package com.aegis.circuitbreaker;

import java.time.Duration;
import java.util.Objects;

/**
 * Immutable, builder-constructed configuration for a {@link CircuitBreaker}.
 *
 * <pre>{@code
 * CircuitBreakerConfig config = CircuitBreakerConfig.builder()
 *         .failureThreshold(5)
 *         .openTimeout(Duration.ofSeconds(10))
 *         .halfOpenTrialCalls(3)
 *         .build();
 * }</pre>
 */
public final class CircuitBreakerConfig {

    private final int failureThreshold;
    private final Duration openTimeout;
    private final int halfOpenTrialCalls;

    private CircuitBreakerConfig(Builder builder) {
        this.failureThreshold = builder.failureThreshold;
        this.openTimeout = builder.openTimeout;
        this.halfOpenTrialCalls = builder.halfOpenTrialCalls;
    }

    /** Consecutive failures (while CLOSED) needed to trip the breaker open. */
    public int getFailureThreshold() {
        return failureThreshold;
    }

    /** How long the breaker stays OPEN before allowing half-open trial calls. */
    public Duration getOpenTimeout() {
        return openTimeout;
    }

    /** Number of trial calls that must succeed in HALF_OPEN before closing again. */
    public int getHalfOpenTrialCalls() {
        return halfOpenTrialCalls;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private int failureThreshold = 5;
        private Duration openTimeout = Duration.ofSeconds(10);
        private int halfOpenTrialCalls = 3;

        public Builder failureThreshold(int failureThreshold) {
            if (failureThreshold <= 0) {
                throw new IllegalArgumentException(
                        "failureThreshold must be positive, got " + failureThreshold);
            }
            this.failureThreshold = failureThreshold;
            return this;
        }

        public Builder openTimeout(Duration openTimeout) {
            Objects.requireNonNull(openTimeout, "openTimeout must not be null");
            if (openTimeout.isZero() || openTimeout.isNegative()) {
                throw new IllegalArgumentException("openTimeout must be positive, got " + openTimeout);
            }
            this.openTimeout = openTimeout;
            return this;
        }

        public Builder halfOpenTrialCalls(int halfOpenTrialCalls) {
            if (halfOpenTrialCalls <= 0) {
                throw new IllegalArgumentException(
                        "halfOpenTrialCalls must be positive, got " + halfOpenTrialCalls);
            }
            this.halfOpenTrialCalls = halfOpenTrialCalls;
            return this;
        }

        public CircuitBreakerConfig build() {
            return new CircuitBreakerConfig(this);
        }
    }
}
