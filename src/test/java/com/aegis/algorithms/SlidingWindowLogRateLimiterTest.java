package com.aegis.algorithms;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SlidingWindowLogRateLimiterTest {

    @Test
    void allowsUpToLimitWithinWindowThenRejects() {
        SlidingWindowLogRateLimiter limiter = new SlidingWindowLogRateLimiter(5, Duration.ofSeconds(10));

        for (int i = 0; i < 5; i++) {
            assertTrue(limiter.tryAcquire(), "request " + i + " should be within the limit");
        }
        assertFalse(limiter.tryAcquire(), "6th request should exceed the limit");
        assertEquals(5, limiter.currentCount());
    }

    @Test
    void allowsAgainOnceOldRequestsExpireOutOfTheWindow() throws InterruptedException {
        SlidingWindowLogRateLimiter limiter = new SlidingWindowLogRateLimiter(2, Duration.ofMillis(150));

        assertTrue(limiter.tryAcquire());
        assertTrue(limiter.tryAcquire());
        assertFalse(limiter.tryAcquire());

        Thread.sleep(200); // comfortably past the window

        assertTrue(limiter.tryAcquire(), "both slots should have expired out of the window");
        assertTrue(limiter.tryAcquire());
    }

    @Test
    void multiPermitAcquireIsAllOrNothing() {
        SlidingWindowLogRateLimiter limiter = new SlidingWindowLogRateLimiter(5, Duration.ofSeconds(10));

        assertTrue(limiter.tryAcquire(3));
        assertFalse(limiter.tryAcquire(3), "only 2 slots remain, requesting 3 must fail entirely");
        assertEquals(3, limiter.currentCount());
        assertTrue(limiter.tryAcquire(2));
    }

    @Test
    void rejectsInvalidConstructorArguments() {
        assertThrows(IllegalArgumentException.class,
                () -> new SlidingWindowLogRateLimiter(0, Duration.ofSeconds(1)));
        assertThrows(IllegalArgumentException.class,
                () -> new SlidingWindowLogRateLimiter(5, Duration.ZERO));
        assertThrows(IllegalArgumentException.class,
                () -> new SlidingWindowLogRateLimiter(5, Duration.ofSeconds(-1)));
    }

    @Test
    void rejectsNonPositivePermitRequests() {
        SlidingWindowLogRateLimiter limiter = new SlidingWindowLogRateLimiter(5, Duration.ofSeconds(1));
        assertThrows(IllegalArgumentException.class, () -> limiter.tryAcquire(0));
        assertThrows(IllegalArgumentException.class, () -> limiter.tryAcquire(-2));
    }
}
