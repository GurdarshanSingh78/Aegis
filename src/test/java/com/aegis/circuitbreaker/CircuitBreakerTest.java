package com.aegis.circuitbreaker;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;

class CircuitBreakerTest {

    private static CircuitBreaker breaker(int failureThreshold, Duration openTimeout, int halfOpenTrialCalls) {
        return new CircuitBreaker(CircuitBreakerConfig.builder()
                .failureThreshold(failureThreshold)
                .openTimeout(openTimeout)
                .halfOpenTrialCalls(halfOpenTrialCalls)
                .build());
    }

    @Test
    void startsClosedAndPassesSuccessfulCallsThrough() {
        CircuitBreaker cb = breaker(3, Duration.ofSeconds(10), 2);

        String result = cb.execute(() -> "downstream-result", ex -> "fallback");

        assertEquals("downstream-result", result);
        assertEquals(CircuitState.CLOSED, cb.getState());
    }

    @Test
    void tripsOpenAfterConsecutiveFailuresReachThreshold() {
        CircuitBreaker cb = breaker(3, Duration.ofSeconds(10), 2);
        AtomicInteger downstreamCalls = new AtomicInteger(0);

        for (int i = 0; i < 3; i++) {
            cb.execute(() -> {
                downstreamCalls.incrementAndGet();
                throw new RuntimeException("boom");
            }, ex -> "fallback");
        }
        assertEquals(CircuitState.OPEN, cb.getState());
        assertEquals(3, downstreamCalls.get());

        // Once OPEN, further calls must be short-circuited without ever
        // touching the downstream action.
        Object[] fallbackException = new Object[1];
        String result = cb.execute(() -> {
            downstreamCalls.incrementAndGet();
            return "should not run";
        }, ex -> {
            fallbackException[0] = ex;
            return "short-circuited";
        });

        assertEquals("short-circuited", result);
        assertEquals(3, downstreamCalls.get(), "downstream action must not run while breaker is OPEN");
        assertInstanceOf(CircuitBreakerOpenException.class, fallbackException[0]);
    }

    @Test
    void movesToHalfOpenAfterTimeoutAndClosesOnceTrialsSucceed() throws InterruptedException {
        CircuitBreaker cb = breaker(2, Duration.ofMillis(100), 2);

        // Trip it open.
        for (int i = 0; i < 2; i++) {
            cb.execute(() -> { throw new RuntimeException("boom"); }, ex -> "fallback");
        }
        assertEquals(CircuitState.OPEN, cb.getState());

        Thread.sleep(150);
        assertEquals(CircuitState.HALF_OPEN, cb.getState(), "should allow trial calls once the timeout elapses");

        // Two successful trial calls should close the breaker again.
        cb.execute(() -> "ok-1", ex -> "fallback");
        cb.execute(() -> "ok-2", ex -> "fallback");

        assertEquals(CircuitState.CLOSED, cb.getState());
    }

    @Test
    void aFailureDuringHalfOpenSendsItStraightBackToOpen() throws InterruptedException {
        CircuitBreaker cb = breaker(1, Duration.ofMillis(100), 3);

        cb.execute(() -> { throw new RuntimeException("boom"); }, ex -> "fallback");
        assertEquals(CircuitState.OPEN, cb.getState());

        Thread.sleep(150);
        assertEquals(CircuitState.HALF_OPEN, cb.getState());

        cb.execute(() -> { throw new RuntimeException("still broken"); }, ex -> "fallback");

        assertEquals(CircuitState.OPEN, cb.getState(),
                "a single failed trial call should re-trip the breaker rather than allowing more trials");
    }

    @Test
    void fallbackReceivesTheExactExceptionThrownByTheAction() {
        CircuitBreaker cb = breaker(5, Duration.ofSeconds(10), 2);
        IllegalStateException thrown = new IllegalStateException("specific failure");

        Object[] captured = new Object[1];
        cb.execute(() -> { throw thrown; }, ex -> {
            captured[0] = ex;
            return null;
        });

        assertSame(thrown, captured[0], "fallback should receive the original exception instance, not a wrapper");
        assertEquals(CircuitState.CLOSED, cb.getState(), "one failure below threshold should not trip the breaker");
    }
}
