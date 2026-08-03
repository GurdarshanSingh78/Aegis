package com.aegis.algorithms;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

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
        long windowMillis = 2000;
        long windowNanos = Duration.ofMillis(windowMillis).toNanos();

        // Thread.sleep(x) only guarantees "at least x" -- under real OS
        // scheduling load it can occasionally overshoot by far more than
        // expected, which previously made this test flaky in both
        // directions (undershooting the boundary, or overshooting straight
        // past the very window we meant to observe). Instead of guessing a
        // sleep duration, we actively poll for the window index to change,
        // and if we ever overshoot by more than one window in a single
        // scheduling gap, we discard that attempt and retry with a fresh
        // limiter rather than asserting on a scenario we didn't set up.
        for (int attempt = 0; attempt < 3; attempt++) {
            SlidingWindowCounterRateLimiter limiter =
                    new SlidingWindowCounterRateLimiter(4, Duration.ofMillis(windowMillis));

            for (int i = 0; i < 4; i++) {
                assertTrue(limiter.tryAcquire());
            }
            assertFalse(limiter.tryAcquire());

            long fillIndex = System.nanoTime() / windowNanos;
            while (System.nanoTime() / windowNanos == fillIndex) {
                Thread.sleep(10);
            }

            if (System.nanoTime() / windowNanos != fillIndex + 1) {
                continue; // skipped straight past the window we wanted to observe; retry
            }

            int allowedRightAfterRollover = 0;
            for (int i = 0; i < 4; i++) {
                if (limiter.tryAcquire()) {
                    allowedRightAfterRollover++;
                }
            }

            // Advance several full window lengths so the window immediately
            // before the next burst is guaranteed to have never been touched,
            // regardless of how much of the burst above got through.
            Thread.sleep(windowMillis * 3L);

            int allowedAfterFullRecovery = 0;
            for (int i = 0; i < 4; i++) {
                if (limiter.tryAcquire()) {
                    allowedAfterFullRecovery++;
                }
            }

            assertTrue(allowedRightAfterRollover < 4,
                    "sliding window counter should smooth traffic across the boundary, "
                            + "not allow a full new burst immediately after rollover");
            assertEquals(4, allowedAfterFullRecovery);
            return;
        }

        fail("Could not reliably observe a single window rollover after 3 attempts "
                + "-- the test environment's scheduling jitter is unusually high.");
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
