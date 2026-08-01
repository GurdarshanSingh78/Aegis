package com.aegis.demo;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A terminal-only alternative to the dashboard's "Start burst" button: fires
 * concurrent HTTP requests at a running {@link com.aegis.dashboard.DashboardServer}
 * from {@code concurrency} client threads for {@code durationSeconds}, then
 * prints a breakdown of how many requests landed in each outcome bucket.
 *
 * <p>Run with {@code AegisDemoApp} already running in another terminal, then:
 * <pre>
 * mvn compile exec:java -Dexec.mainClass=com.aegis.demo.LoadGenerator
 * mvn compile exec:java -Dexec.mainClass=com.aegis.demo.LoadGenerator -Dexec.args="20 30"
 * </pre>
 * where the optional args are {@code concurrency} and {@code durationSeconds}.
 */
public final class LoadGenerator {

    public static void main(String[] args) throws InterruptedException {
        int concurrency = args.length > 0 ? Integer.parseInt(args[0]) : 10;
        int durationSeconds = args.length > 1 ? Integer.parseInt(args[1]) : 20;
        String baseUrl = args.length > 2 ? args[2] : "http://localhost:8080";

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .build();

        AtomicLong success = new AtomicLong();
        AtomicLong rateLimited = new AtomicLong();
        AtomicLong shortCircuited = new AtomicLong();
        AtomicLong downstreamFailure = new AtomicLong();
        AtomicLong other = new AtomicLong();

        System.out.printf("Firing requests from %d client threads for %d seconds against %s ...%n",
                concurrency, durationSeconds, baseUrl);

        ExecutorService pool = Executors.newFixedThreadPool(concurrency);
        long deadline = System.currentTimeMillis() + durationSeconds * 1000L;

        for (int i = 0; i < concurrency; i++) {
            pool.submit(() -> {
                HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/api/resource"))
                        .timeout(Duration.ofSeconds(2))
                        .GET()
                        .build();
                while (System.currentTimeMillis() < deadline) {
                    try {
                        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                        classify(response.body(), success, rateLimited, shortCircuited, downstreamFailure, other);
                    } catch (Exception e) {
                        other.incrementAndGet();
                    }
                }
            });
        }

        pool.shutdown();
        pool.awaitTermination(durationSeconds + 10L, TimeUnit.SECONDS);

        System.out.println();
        System.out.println("Results:");
        System.out.println("  success            = " + success.get());
        System.out.println("  rate limited       = " + rateLimited.get());
        System.out.println("  short-circuited    = " + shortCircuited.get());
        System.out.println("  downstream failure = " + downstreamFailure.get());
        System.out.println("  other/error        = " + other.get());
    }

    private static void classify(
            String body,
            AtomicLong success,
            AtomicLong rateLimited,
            AtomicLong shortCircuited,
            AtomicLong downstreamFailure,
            AtomicLong other) {
        if ("ok".equals(body)) {
            success.incrementAndGet();
        } else if (body.contains("RateLimitExceededException")) {
            rateLimited.incrementAndGet();
        } else if (body.contains("CircuitBreakerOpenException")) {
            shortCircuited.incrementAndGet();
        } else if (body.contains("DownstreamFailureException")) {
            downstreamFailure.incrementAndGet();
        } else {
            other.incrementAndGet();
        }
    }
}
