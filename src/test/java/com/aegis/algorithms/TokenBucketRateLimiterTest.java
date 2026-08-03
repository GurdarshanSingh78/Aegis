package com.aegis.algorithms;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TokenBucketRateLimiterTest {

    @Test
    void allowsBurstUpToCapacityThenRejects() {
        // Refill rate is negligible relative to the test's runtime, so this
        // exercises pure burst behavior off the starting-full bucket.
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(5, 0.001);

        for (int i = 0; i < 5; i++) {
            assertTrue(limiter.tryAcquire(), "token " + i + " should be available from the initial burst");
        }
        assertFalse(limiter.tryAcquire(), "bucket should be empty after the burst");
    }

    @Test
    void refillsTokensOverTime() throws InterruptedException {
        // 100 tokens/sec == 1 token roughly every 10ms.
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(2, 100);

        assertTrue(limiter.tryAcquire());
        assertTrue(limiter.tryAcquire());
        assertFalse(limiter.tryAcquire());

        Thread.sleep(30); // comfortably more than one refill interval

        assertTrue(limiter.tryAcquire(), "a token should have refilled after waiting");
    }

    @Test
    void neverExceedsCapacityEvenAfterLongIdlePeriod() throws InterruptedException {
        // A slow refill rate keeps this test robust on a real machine: at
        // 1000 tokens/sec (1 token/ms), the acquire loop below only needs
        // to take just over 1ms of wall-clock time -- entirely plausible
        // under normal OS scheduling -- to accumulate an extra token
        // mid-loop and make this test flaky. At 1 token/sec, the loop would
        // need to run for over a second to do that.
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(3, 1);

        Thread.sleep(50); // plenty of time to "overflow" past capacity if refill were unbounded

        int allowed = 0;
        for (int i = 0; i < 10; i++) {
            if (limiter.tryAcquire()) {
                allowed++;
            }
        }
        assertEquals(3, allowed, "refill must be capped at capacity, not accumulate without bound");
    }

    @Test
    void multiPermitAcquireIsAllOrNothing() {
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(5, 0.001);

        assertTrue(limiter.tryAcquire(3));
        assertFalse(limiter.tryAcquire(3), "only 2 tokens remain, requesting 3 must fail entirely");
        assertTrue(limiter.tryAcquire(2), "the 2 remaining tokens should still be acquirable");
    }

    @Test
    void rejectsInvalidConstructorArguments() {
        assertThrows(IllegalArgumentException.class, () -> new TokenBucketRateLimiter(0, 1));
        assertThrows(IllegalArgumentException.class, () -> new TokenBucketRateLimiter(-1, 1));
        assertThrows(IllegalArgumentException.class, () -> new TokenBucketRateLimiter(1, 0));
        assertThrows(IllegalArgumentException.class, () -> new TokenBucketRateLimiter(1, -5));
    }

    @Test
    void rejectsNonPositivePermitRequests() {
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(5, 1);
        assertThrows(IllegalArgumentException.class, () -> limiter.tryAcquire(0));
        assertThrows(IllegalArgumentException.class, () -> limiter.tryAcquire(-1));
    }
}
