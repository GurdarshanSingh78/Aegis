package com.aegis.algorithms;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SlidingWindowCounterRateLimiterTest {

    @Test
    void allowsUpToLimitInFirstWindowThenRejects() {
        SlidingWindowCounterRateLimiter limiter =
                new SlidingWindowCounterRateLimiter(5, Duration.ofSeconds(10));

        for (int i = 0; i < 5; i++) {
            assertTrue(limiter.tryAcquire(), "request " + i + " should be within the limit");
        }
        assertFalse(limiter.tryAcquire(), "6th request should exceed the limit");
    }

    @Test
    void smoothsTrafficAcrossTheWindowBoundary() throws InterruptedException {
        SlidingWindowCounterRateLimiter limiter =
                new SlidingWindowCounterRateLimiter(4, Duration.ofMillis(500));

        // Fill the first window completely.
        for (int i = 0; i < 4; i++) {
            assertTrue(limiter.tryAcquire());
        }
        assertFalse(limiter.tryAcquire());

        // Jump just past the window boundary. The previous window's count is
        // still weighted heavily into the estimate at this point, so a fresh
        // burst of 4 should NOT all be admitted immediately -- this is the
        // whole point of the algorithm versus a naive fixed-window counter.
        Thread.sleep(520);
        int allowedRightAfterRollover = 0;
        for (int i = 0; i < 4; i++) {
            if (limiter.tryAcquire()) {
                allowedRightAfterRollover++;
            }
        }
        assertTrue(allowedRightAfterRollover < 4,
                "sliding window counter should smooth traffic across the boundary, "
                        + "not allow a full new burst immediately after rollover");

        // Once enough time has passed that the old window no longer factors
        // into the estimate at all, a full burst should be allowed again.
        Thread.sleep(700);
        int allowedAfterFullRecovery = 0;
        for (int i = 0; i < 4; i++) {
            if (limiter.tryAcquire()) {
                allowedAfterFullRecovery++;
            }
        }
        assertEquals(4, allowedAfterFullRecovery);
    }

    @Test
    void rejectsInvalidConstructorArguments() {
        assertThrows(IllegalArgumentException.class,
                () -> new SlidingWindowCounterRateLimiter(0, Duration.ofSeconds(1)));
        assertThrows(IllegalArgumentException.class,
                () -> new SlidingWindowCounterRateLimiter(5, Duration.ZERO));
        assertThrows(IllegalArgumentException.class,
                () -> new SlidingWindowCounterRateLimiter(5, Duration.ofSeconds(-1)));
    }

    @Test
    void rejectsNonPositivePermitRequests() {
        SlidingWindowCounterRateLimiter limiter =
                new SlidingWindowCounterRateLimiter(5, Duration.ofSeconds(1));
        assertThrows(IllegalArgumentException.class, () -> limiter.tryAcquire(0));
        assertThrows(IllegalArgumentException.class, () -> limiter.tryAcquire(-3));
    }
}
