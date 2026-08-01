package com.aegis.demo;

import com.aegis.circuitbreaker.CircuitBreakerConfig;
import com.aegis.core.RateLimiterAlgorithm;
import com.aegis.core.RateLimiterConfig;
import com.aegis.dashboard.DashboardServer;
import com.aegis.dashboard.DemoDownstreamService;
import com.aegis.gateway.AegisGateway;

import java.time.Duration;

/**
 * Entry point for the live demo: wires an {@link AegisGateway} around a
 * simulated flaky downstream, then serves it behind {@link DashboardServer}.
 *
 * <p>Run with {@code mvn compile exec:java}, then open
 * {@code http://localhost:8080/} and click "Start burst" to watch requests
 * get rate-limited and the circuit breaker trip and recover in real time.
 *
 * <p>Listens on the port in the {@code PORT} environment variable if set
 * (the convention every common hosting platform -- Render, Railway, Fly.io
 * -- uses to tell a container which port to bind), falling back to 8080 for
 * local runs.
 *
 * <p>Pass a different algorithm as the first CLI argument to compare them
 * side by side, e.g. {@code mvn compile exec:java
 * -Dexec.args=SLIDING_WINDOW_COUNTER}. Valid values: {@code TOKEN_BUCKET}
 * (default), {@code SLIDING_WINDOW_LOG}, {@code SLIDING_WINDOW_COUNTER}.
 */
public final class AegisDemoApp {

    private static final int DEFAULT_PORT = 8080;

    public static void main(String[] args) throws Exception {
        RateLimiterAlgorithm algorithm = parseAlgorithm(args);
        int port = resolvePort();

        RateLimiterConfig.Builder rateLimiterBuilder = RateLimiterConfig.builder(algorithm)
                .capacity(20);
        if (algorithm == RateLimiterAlgorithm.TOKEN_BUCKET) {
            rateLimiterBuilder.refillRatePerSecond(5);
        } else {
            rateLimiterBuilder.windowSize(Duration.ofSeconds(2));
        }

        AegisGateway gateway = AegisGateway.builder()
                .rateLimiter(rateLimiterBuilder.build())
                .circuitBreaker(CircuitBreakerConfig.builder()
                        .failureThreshold(5)
                        .openTimeout(Duration.ofSeconds(5))
                        .halfOpenTrialCalls(3)
                        .build())
                .build();

        DemoDownstreamService downstream = new DemoDownstreamService();
        DashboardServer server = new DashboardServer(gateway, downstream, port);
        server.start();

        System.out.println("Rate limiter algorithm: " + algorithm);
        System.out.println("Listening on port " + port + ". Locally, open http://localhost:" + port
                + "/ and click \"Start burst\".");
        System.out.println("Press Ctrl+C to stop.");

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\nShutting down Aegis dashboard.");
            server.stop();
        }));
    }

    private static int resolvePort() {
        String env = System.getenv("PORT");
        if (env == null || env.isBlank()) {
            return DEFAULT_PORT;
        }
        try {
            return Integer.parseInt(env.trim());
        } catch (NumberFormatException e) {
            System.err.println("Ignoring invalid PORT env var '" + env + "', using " + DEFAULT_PORT);
            return DEFAULT_PORT;
        }
    }

    private static RateLimiterAlgorithm parseAlgorithm(String[] args) {
        if (args.length == 0) {
            return RateLimiterAlgorithm.TOKEN_BUCKET;
        }
        try {
            return RateLimiterAlgorithm.valueOf(args[0].trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            System.err.println("Unknown algorithm '" + args[0] + "', falling back to TOKEN_BUCKET. "
                    + "Valid values: TOKEN_BUCKET, SLIDING_WINDOW_LOG, SLIDING_WINDOW_COUNTER");
            return RateLimiterAlgorithm.TOKEN_BUCKET;
        }
    }
}
