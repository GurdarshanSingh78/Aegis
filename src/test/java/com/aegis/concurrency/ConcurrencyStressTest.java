package com.aegis.concurrency;

import com.aegis.algorithms.SlidingWindowCounterRateLimiter;
import com.aegis.algorithms.SlidingWindowLogRateLimiter;
import com.aegis.algorithms.TokenBucketRateLimiter;
import com.aegis.core.RateLimiter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The whole point of building these algorithms by hand instead of just
 * calling a library is proving they're actually thread-safe. Each test
 * hammers a limiter configured with a hard capacity of {@code LIMIT} from
 * many threads at once, released simultaneously via a {@link CountDownLatch}
 * so contention is as adversarial as this JVM can make it, then asserts the
 * number of granted permits is exactly {@code LIMIT} -- not one more (a
 * race let two threads both "win" the last permit) and not one fewer (an
 * overly conservative lock lost concurrency for no reason).
 */
class ConcurrencyStressTest {

    private static final int LIMIT = 100;
    private static final int THREAD_COUNT = 20;
    private static final int ATTEMPTS_PER_THREAD = 20; // 400 total attempts against a limit of 100

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void tokenBucketNeverGrantsMorePermitsThanCapacityUnderConcurrentLoad() throws InterruptedException {
        // Refill rate is negligible relative to the test's runtime, isolating
        // pure concurrent-burst correctness from the refill mechanism.
        RateLimiter limiter = new TokenBucketRateLimiter(LIMIT, 0.001);
        int granted = hammerConcurrently(limiter);
        assertEquals(LIMIT, granted);
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void slidingWindowLogNeverGrantsMorePermitsThanLimitUnderConcurrentLoad() throws InterruptedException {
        RateLimiter limiter = new SlidingWindowLogRateLimiter(LIMIT, Duration.ofSeconds(30));
        int granted = hammerConcurrently(limiter);
        assertEquals(LIMIT, granted);
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void slidingWindowCounterNeverGrantsMorePermitsThanLimitUnderConcurrentLoad() throws InterruptedException {
        RateLimiter limiter = new SlidingWindowCounterRateLimiter(LIMIT, Duration.ofSeconds(30));
        int granted = hammerConcurrently(limiter);
        assertEquals(LIMIT, granted);
    }

    /**
     * Starts {@link #THREAD_COUNT} threads, each attempting
     * {@link #ATTEMPTS_PER_THREAD} acquires, all released at once via a
     * latch, and returns the total number of successful acquires.
     */
    private static int hammerConcurrently(RateLimiter limiter) throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(THREAD_COUNT);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(THREAD_COUNT);
        AtomicInteger granted = new AtomicInteger(0);

        for (int i = 0; i < THREAD_COUNT; i++) {
            pool.submit(() -> {
                try {
                    startLatch.await();
                    for (int attempt = 0; attempt < ATTEMPTS_PER_THREAD; attempt++) {
                        if (limiter.tryAcquire()) {
                            granted.incrementAndGet();
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown(); // release every thread at (as close as the JVM allows to) the same instant
        doneLatch.await();
        pool.shutdown();
        pool.awaitTermination(5, TimeUnit.SECONDS);

        return granted.get();
    }
}
