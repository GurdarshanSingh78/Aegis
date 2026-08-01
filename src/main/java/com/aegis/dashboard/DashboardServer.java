package com.aegis.dashboard;

import com.aegis.gateway.AegisGateway;
import com.aegis.metrics.MetricsSnapshot;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * A tiny embedded HTTP server built on the JDK's built-in
 * {@code com.sun.net.httpserver} (no Spring, no external web framework --
 * one less thing standing between "clone the repo" and "see it work")
 * exposing three routes:
 *
 * <ul>
 *   <li>{@code GET /} -- the dashboard page ({@link DashboardHtml})</li>
 *   <li>{@code GET /api/resource} -- a demo endpoint routed through the
 *       {@link AegisGateway}, backed by {@link DemoDownstreamService}</li>
 *   <li>{@code GET /metrics/stream} -- a Server-Sent Events stream pushing
 *       a {@link MetricsSnapshot} as JSON twice a second</li>
 * </ul>
 */
public final class DashboardServer {

    private final AegisGateway gateway;
    private final DemoDownstreamService downstream;
    private final int port;
    private HttpServer server;

    public DashboardServer(AegisGateway gateway, DemoDownstreamService downstream, int port) {
        this.gateway = gateway;
        this.downstream = downstream;
        this.port = port;
    }

    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(port), 0);
        ExecutorService executor = Executors.newFixedThreadPool(16);
        server.setExecutor(executor);

        server.createContext("/", this::handleDashboardPage);
        server.createContext("/api/resource", this::handleApiResource);
        server.createContext("/metrics/stream", this::handleMetricsStream);

        server.start();
        System.out.println("Aegis dashboard running at http://localhost:" + port + "/");
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    private void handleDashboardPage(HttpExchange exchange) throws IOException {
        byte[] body = DashboardHtml.PAGE.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
        exchange.sendResponseHeaders(200, body.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(body);
        }
    }

    private void handleApiResource(HttpExchange exchange) throws IOException {
        // "FALLBACK" covers three distinct causes: rate limit exceeded, circuit
        // open (short-circuited), or the downstream call itself throwing --
        // the exception class name tells the caller which one happened.
        String result = gateway.call(
                downstream::call,
                ex -> "FALLBACK: " + ex.getClass().getSimpleName() + " - " + ex.getMessage());

        byte[] body = result.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
        exchange.sendResponseHeaders(200, body.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(body);
        }
    }

    private void handleMetricsStream(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "text/event-stream; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-cache");
        exchange.getResponseHeaders().set("Connection", "keep-alive");
        exchange.sendResponseHeaders(200, 0);

        try (OutputStream os = exchange.getResponseBody()) {
            while (true) {
                MetricsSnapshot snapshot = gateway.metricsSnapshot();
                String chunk = "data: " + snapshot.toJson() + "\n\n";
                os.write(chunk.getBytes(StandardCharsets.UTF_8));
                os.flush();
                Thread.sleep(500);
            }
        } catch (IOException e) {
            // Client disconnected (closed tab, navigated away). Nothing to clean up
            // beyond what try-with-resources already does.
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
