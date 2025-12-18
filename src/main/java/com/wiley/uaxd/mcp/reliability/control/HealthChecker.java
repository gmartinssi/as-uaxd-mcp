package com.wiley.uaxd.mcp.reliability.control;

import com.wiley.uaxd.mcp.log.boundary.Log;
import com.wiley.uaxd.mcp.reliability.boundary.ServiceRegistry;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Background health checker for services.
 * Periodically checks service health endpoints and updates circuit breakers.
 */
public class HealthChecker {

    private final ServiceRegistry registry;
    private final HttpClient httpClient;
    private final ScheduledExecutorService scheduler;
    private final Map<String, String> healthEndpoints;

    private static final Duration CHECK_INTERVAL = Duration.ofSeconds(30);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(5);

    public HealthChecker(ServiceRegistry registry) {
        this.registry = registry;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(REQUEST_TIMEOUT)
            .build();
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "health-checker");
            t.setDaemon(true);
            return t;
        });

        // Health check endpoints for VPN-required services
        this.healthEndpoints = Map.of(
            "GetUAXDArticles", "http://host.docker.internal:8080/actuator/health",
            "GetASArticles", "http://as-app-wqa.aws.wiley.com:8080/actuator/health"
            // GetRexArticles doesn't need health check (external, reliable service)
        );
    }

    /**
     * Start the background health checker.
     */
    public void start() {
        Log.info("Starting health checker (interval: " + CHECK_INTERVAL.toSeconds() + "s)");
        scheduler.scheduleAtFixedRate(
            this::checkAllServices,
            CHECK_INTERVAL.toSeconds(),
            CHECK_INTERVAL.toSeconds(),
            TimeUnit.SECONDS
        );
    }

    /**
     * Stop the health checker.
     */
    public void stop() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        Log.info("Health checker stopped");
    }

    /**
     * Check all registered services.
     */
    private void checkAllServices() {
        for (Map.Entry<String, String> entry : healthEndpoints.entrySet()) {
            String serviceName = entry.getKey();
            String endpoint = entry.getValue();
            checkService(serviceName, endpoint);
        }
    }

    /**
     * Check a single service's health.
     */
    private void checkService(String serviceName, String endpoint) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .timeout(REQUEST_TIMEOUT)
                .GET()
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                registry.recordSuccess(serviceName);
                Log.debug("Health check passed: " + serviceName);
            } else {
                registry.recordFailure(serviceName);
                Log.info("Health check failed: " + serviceName + " (status: " + response.statusCode() + ")");
            }
        } catch (Exception e) {
            registry.recordFailure(serviceName);
            Log.debug("Health check error: " + serviceName + " - " + e.getMessage());
        }
    }

    /**
     * Perform an immediate health check for a specific service.
     */
    public boolean checkNow(String serviceName) {
        String endpoint = healthEndpoints.get(serviceName);
        if (endpoint == null) {
            return true; // No health endpoint, assume healthy
        }

        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .timeout(REQUEST_TIMEOUT)
                .GET()
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() >= 200 && response.statusCode() < 300;
        } catch (Exception e) {
            return false;
        }
    }
}
