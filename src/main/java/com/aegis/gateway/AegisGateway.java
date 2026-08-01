package com.aegis.gateway;

import com.aegis.circuitbreaker.CircuitBreaker;
import com.aegis.circuitbreaker.CircuitBreakerConfig;
import com.aegis.circuitbreaker.CircuitBreakerOpenException;
import com.aegis.core.RateLimitExceededException;
import com.aegis.core.RateLimiter;
import com.aegis.core.RateLimiterConfig;
import com.aegis.core.RateLimiterFactory;
import com.aegis.metrics.MetricsRegistry;
import com.aegis.metrics.MetricsSnapshot;

import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Facade over a {@link RateLimiter} and a {@link CircuitBreaker} that a
 * downstream call is routed through:
 *
 * <pre>{@code
 * AegisGateway gateway = AegisGateway.builder()
 *         .rateLimiter(RateLimiterConfig.builder(RateLimiterAlgorithm.TOKEN_BUCKET)
 *                 .capacity(50)
 *                 .refillRatePerSecond(10)
 *                 .build())
 *         .circuitBreaker(CircuitBreakerConfig.builder()
 *                 .failureThreshold(5)
 *                 .openTimeout(Duration.ofSeconds(10))
 *                 .build())
 *         .build();
 *
 * String result = gateway.call(
 *         () -> downstreamClient.fetch(),
 *         ex -> "fallback response"
 * );
 * }</pre>
 *
 * <p>Every call goes through the rate limiter first (cheap, local, no
 * network) and only reaches the circuit breaker -- and potentially
 * downstream -- if a permit was available. This is the same order a real
 * gateway uses: don't even ask "is downstream healthy?" for traffic you were
 * going to shed anyway.
 */
public final class AegisGateway {

    private final RateLimiter rateLimiter;
    private final CircuitBreaker circuitBreaker;
    private final MetricsRegistry metrics = new MetricsRegistry();

    private AegisGateway(RateLimiter rateLimiter, CircuitBreaker circuitBreaker) {
        this.rateLimiter = rateLimiter;
        this.circuitBreaker = circuitBreaker;
    }

    /**
     * Routes {@code action} through the rate limiter and circuit breaker.
     * Returns whatever {@code action} returns on success, or the result of
     * {@code fallback} if the call was rejected (rate limit), short-circuited
     * (breaker open), or {@code action} itself threw.
     */
    public <T> T call(Supplier<T> action, Function<Exception, T> fallback) {
        if (!rateLimiter.tryAcquire()) {
            metrics.recordRejected();
            return fallback.apply(new RateLimitExceededException("rate limit exceeded"));
        }
        metrics.recordAllowed();

        return circuitBreaker.execute(
                () -> {
                    T result = action.get();
                    metrics.recordSuccess();
                    return result;
                },
                ex -> {
                    if (ex instanceof CircuitBreakerOpenException) {
                        metrics.recordShortCircuited();
                    } else {
                        metrics.recordFailure();
                    }
                    return fallback.apply(ex);
                });
    }

    /** Current traffic counters and circuit state, for logging or a live dashboard. */
    public MetricsSnapshot metricsSnapshot() {
        return metrics.snapshot(circuitBreaker.getState());
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private RateLimiterConfig rateLimiterConfig;
        private CircuitBreakerConfig circuitBreakerConfig = CircuitBreakerConfig.builder().build();

        public Builder rateLimiter(RateLimiterConfig config) {
            this.rateLimiterConfig = Objects.requireNonNull(config, "rateLimiterConfig must not be null");
            return this;
        }

        public Builder circuitBreaker(CircuitBreakerConfig config) {
            this.circuitBreakerConfig = Objects.requireNonNull(config, "circuitBreakerConfig must not be null");
            return this;
        }

        public AegisGateway build() {
            Objects.requireNonNull(rateLimiterConfig, "rateLimiter(...) must be called before build()");
            RateLimiter limiter = RateLimiterFactory.create(rateLimiterConfig);
            CircuitBreaker breaker = new CircuitBreaker(circuitBreakerConfig);
            return new AegisGateway(limiter, breaker);
        }
    }
}
