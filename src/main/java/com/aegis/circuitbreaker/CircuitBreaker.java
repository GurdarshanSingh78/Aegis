package com.aegis.circuitbreaker;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * A circuit breaker guarding a downstream call, implementing the standard
 * three-state machine:
 *
 * <ul>
 *   <li><b>CLOSED</b> -- calls pass through normally. Consecutive failures
 *       are counted; hitting {@code failureThreshold} trips the breaker to
 *       OPEN.</li>
 *   <li><b>OPEN</b> -- every call is short-circuited straight to the
 *       fallback, without ever touching the downstream service, for
 *       {@code openTimeout}. This is what makes a circuit breaker
 *       different from a retry loop: it stops calling a service that has
 *       already told you it's unhealthy, instead of hammering it further.</li>
 *   <li><b>HALF_OPEN</b> -- once the timeout elapses, a small number of
 *       trial calls are let through. If {@code halfOpenTrialCalls} of them
 *       succeed, the breaker closes again; a single failure sends it
 *       straight back to OPEN (and restarts the timeout) rather than
 *       risking a slow trickle of failed trials against a still-broken
 *       service.</li>
 * </ul>
 *
 * <p>Every piece of mutable state is either an {@link AtomicReference} or an
 * atomic counter, and every state transition goes through a
 * compare-and-swap, so concurrent callers can never both "win" the same
 * trip-to-OPEN or close-from-HALF_OPEN transition.
 */
public final class CircuitBreaker {

    private final int failureThreshold;
    private final long openTimeoutNanos;
    private final int halfOpenTrialCalls;

    private final AtomicReference<CircuitState> state = new AtomicReference<>(CircuitState.CLOSED);
    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
    private final AtomicLong openedAtNanos = new AtomicLong(0);
    private final AtomicInteger halfOpenCallsInFlight = new AtomicInteger(0);
    private final AtomicInteger halfOpenSuccesses = new AtomicInteger(0);

    public CircuitBreaker(CircuitBreakerConfig config) {
        this.failureThreshold = config.getFailureThreshold();
        this.openTimeoutNanos = config.getOpenTimeout().toNanos();
        this.halfOpenTrialCalls = config.getHalfOpenTrialCalls();
    }

    /**
     * Runs {@code action} if the breaker allows it, recording the outcome
     * and driving the state machine; otherwise invokes {@code fallback} with
     * a {@link CircuitBreakerOpenException} without ever calling
     * {@code action}. If {@code action} throws, {@code fallback} is invoked
     * with that exception instead, after the failure has been recorded.
     *
     * <p>{@code action} must wrap any checked exceptions it needs to throw
     * in an unchecked exception, since {@link Supplier} cannot declare
     * checked exceptions.
     */
    public <T> T execute(Supplier<T> action, Function<Exception, T> fallback) {
        if (!allowRequest()) {
            return fallback.apply(new CircuitBreakerOpenException(
                    "circuit breaker is " + state.get() + "; call short-circuited"));
        }
        try {
            T result = action.get();
            onSuccess();
            return result;
        } catch (Exception e) {
            onFailure();
            return fallback.apply(e);
        }
    }

    /** Current state, after opportunistically applying an OPEN-&gt;HALF_OPEN transition if due. */
    public CircuitState getState() {
        tryTransitionFromOpenToHalfOpen();
        return state.get();
    }

    private boolean allowRequest() {
        tryTransitionFromOpenToHalfOpen();
        CircuitState current = state.get();

        if (current == CircuitState.OPEN) {
            return false;
        }
        if (current == CircuitState.HALF_OPEN) {
            int inFlight = halfOpenCallsInFlight.incrementAndGet();
            if (inFlight > halfOpenTrialCalls) {
                halfOpenCallsInFlight.decrementAndGet();
                return false;
            }
            return true;
        }
        return true; // CLOSED
    }

    private void tryTransitionFromOpenToHalfOpen() {
        if (state.get() != CircuitState.OPEN) {
            return;
        }
        long elapsed = System.nanoTime() - openedAtNanos.get();
        if (elapsed >= openTimeoutNanos) {
            if (state.compareAndSet(CircuitState.OPEN, CircuitState.HALF_OPEN)) {
                halfOpenCallsInFlight.set(0);
                halfOpenSuccesses.set(0);
            }
        }
    }

    private void onSuccess() {
        if (state.get() == CircuitState.HALF_OPEN) {
            halfOpenCallsInFlight.decrementAndGet();
            int successes = halfOpenSuccesses.incrementAndGet();
            if (successes >= halfOpenTrialCalls) {
                if (state.compareAndSet(CircuitState.HALF_OPEN, CircuitState.CLOSED)) {
                    consecutiveFailures.set(0);
                }
            }
        } else {
            consecutiveFailures.set(0);
        }
    }

    private void onFailure() {
        CircuitState current = state.get();
        if (current == CircuitState.HALF_OPEN) {
            halfOpenCallsInFlight.decrementAndGet();
            trip();
        } else if (current == CircuitState.CLOSED) {
            int failures = consecutiveFailures.incrementAndGet();
            if (failures >= failureThreshold) {
                trip();
            }
        }
        // A failure observed while already OPEN (a straggler call that was
        // in flight when the breaker tripped) doesn't need to do anything
        // further -- the breaker is already open.
    }

    private void trip() {
        state.set(CircuitState.OPEN);
        openedAtNanos.set(System.nanoTime());
        consecutiveFailures.set(0);
    }
}
