package com.aegis.algorithms;

import com.aegis.core.RateLimiter;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Classic token bucket: the bucket starts full with {@code capacity} tokens
 * and refills continuously at {@code refillTokensPerSecond}. Each acquire
 * lazily "catches the bucket up" to the current time before checking
 * whether enough tokens are available.
 *
 * <h2>Concurrency design</h2>
 * No lock is held at any point. Two independent pieces of mutable state are
 * each owned by their own {@link AtomicLong} and updated with a
 * compare-and-swap retry loop:
 *
 * <ul>
 *   <li>{@code lastRefillNanos} -- the timestamp of the last refill. A
 *       thread only applies a refill if it wins the CAS on this field,
 *       which means concurrent threads can never double-apply the same
 *       slice of elapsed time.</li>
 *   <li>{@code scaledTokens} -- the token count, scaled up by
 *       {@code PRECISION} so fractional tokens-per-nanosecond refill rates
 *       don't get lost to integer truncation. Debits are applied with a
 *       CAS retry loop instead of a lock, so acquiring is wait-free under
 *       most conditions and never blocks a thread on another thread's I/O
 *       or scheduling delay.</li>
 * </ul>
 *
 * <p>This trades a small amount of refill "fuzziness" under very heavy
 * contention (a thread that loses the timestamp CAS simply skips applying
 * that particular slice, trusting that whoever won already applied it) for
 * avoiding a shared lock entirely -- the right trade for a hot path that
 * may be called on every single request.
 */
public final class TokenBucketRateLimiter implements RateLimiter {

    /** Scale factor so we can track fractional tokens using integer arithmetic. */
    private static final long PRECISION = 1_000_000L;

    private final long capacityScaled;
    private final double refillPerNano;

    private final AtomicLong scaledTokens;
    private final AtomicLong lastRefillNanos;

    /**
     * @param capacity              maximum burst size, in whole tokens
     * @param refillTokensPerSecond sustained throughput, in tokens/second
     */
    public TokenBucketRateLimiter(long capacity, double refillTokensPerSecond) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be positive, got " + capacity);
        }
        if (refillTokensPerSecond <= 0) {
            throw new IllegalArgumentException(
                    "refillTokensPerSecond must be positive, got " + refillTokensPerSecond);
        }
        this.capacityScaled = capacity * PRECISION;
        this.refillPerNano = refillTokensPerSecond / 1_000_000_000.0;
        // Buckets start full, matching the usual token-bucket convention of
        // allowing an immediate burst up to capacity.
        this.scaledTokens = new AtomicLong(capacityScaled);
        this.lastRefillNanos = new AtomicLong(System.nanoTime());
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
        long needed = (long) permits * PRECISION;

        refill();

        while (true) {
            long current = scaledTokens.get();
            if (current < needed) {
                return false;
            }
            if (scaledTokens.compareAndSet(current, current - needed)) {
                return true;
            }
            // Someone else mutated scaledTokens between our read and our CAS.
            // Loop and re-check with a fresh read rather than blocking.
        }
    }

    /** Number of whole tokens currently available, for metrics/diagnostics. */
    public long availableTokens() {
        refill();
        return scaledTokens.get() / PRECISION;
    }

    private void refill() {
        long now = System.nanoTime();
        long last = lastRefillNanos.get();
        long elapsed = now - last;
        if (elapsed <= 0) {
            return;
        }
        // Only the thread that successfully claims this time slice applies
        // it, so concurrent refill() calls never add tokens twice for the
        // same elapsed interval.
        if (lastRefillNanos.compareAndSet(last, now)) {
            long tokensToAdd = (long) (elapsed * refillPerNano * PRECISION);
            if (tokensToAdd > 0) {
                scaledTokens.updateAndGet(t -> Math.min(capacityScaled, t + tokensToAdd));
            }
        }
    }
}
