package com.aegis.dashboard;

import java.util.concurrent.ThreadLocalRandom;

/**
 * A stand-in for a real downstream dependency, used only by the demo
 * dashboard -- not part of the Aegis library. It cycles between a healthy
 * period (rare, fast failures) and an unhealthy period (frequent, slow
 * failures) on a fixed wall-clock schedule, so a live demo actually watches
 * the circuit breaker trip and recover instead of sitting CLOSED forever.
 */
public final class DemoDownstreamService {

    private static final long HEALTHY_MILLIS = 15_000;
    private static final long UNHEALTHY_MILLIS = 10_000;
    private static final long CYCLE_MILLIS = HEALTHY_MILLIS + UNHEALTHY_MILLIS;

    private final long startedAtMillis = System.currentTimeMillis();

    /**
     * Simulates one call to the downstream service: a short sleep to stand
     * in for network latency, then either a normal return or a thrown
     * {@link DownstreamFailureException}, with odds depending on which part
     * of the health cycle the service is currently in.
     */
    public String call() {
        boolean healthy = isHealthyPeriod();
        sleepQuietly(healthy ? 5 : 40);

        double failureChance = healthy ? 0.03 : 0.85;
        if (ThreadLocalRandom.current().nextDouble() < failureChance) {
            throw new DownstreamFailureException(healthy ? "transient error" : "downstream overloaded");
        }
        return "ok";
    }

    public boolean isHealthyPeriod() {
        long elapsed = (System.currentTimeMillis() - startedAtMillis) % CYCLE_MILLIS;
        return elapsed < HEALTHY_MILLIS;
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public static final class DownstreamFailureException extends RuntimeException {
        public DownstreamFailureException(String message) {
            super(message);
        }
    }
}
