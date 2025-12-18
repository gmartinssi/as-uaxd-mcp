package com.wiley.uaxd.mcp.reliability.boundary;

import com.wiley.uaxd.mcp.log.boundary.Log;
import com.wiley.uaxd.mcp.reliability.control.CircuitBreaker;
import com.wiley.uaxd.mcp.reliability.entity.ServiceStatus;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry for tracking service availability via circuit breakers.
 */
public class ServiceRegistry {

    private final Map<String, CircuitBreaker> circuitBreakers = new ConcurrentHashMap<>();
    private final Map<String, Boolean> vpnRequiredServices = new ConcurrentHashMap<>();

    // Default circuit breaker settings
    private static final int DEFAULT_FAILURE_THRESHOLD = 3;
    private static final Duration DEFAULT_OPEN_DURATION = Duration.ofMinutes(1);

    public ServiceRegistry() {
        // Register known VPN-required services
        registerService("GetUAXDArticles", true);
        registerService("GetASArticles", true);
        registerService("GetRexArticles", false); // External, no VPN needed
    }

    /**
     * Register a service with the registry.
     */
    public void registerService(String serviceName, boolean requiresVpn) {
        circuitBreakers.computeIfAbsent(serviceName, name -> {
            Log.info("Registering service: " + name + " (VPN required: " + requiresVpn + ")");
            return new CircuitBreaker(name, DEFAULT_FAILURE_THRESHOLD, DEFAULT_OPEN_DURATION);
        });
        vpnRequiredServices.put(serviceName, requiresVpn);
    }

    /**
     * Check if a service is available (circuit not open).
     */
    public boolean isServiceAvailable(String serviceName) {
        CircuitBreaker cb = circuitBreakers.get(serviceName);
        if (cb == null) {
            // Unknown service, assume available
            return true;
        }
        return cb.isAvailable();
    }

    /**
     * Record a successful call for a service.
     */
    public void recordSuccess(String serviceName) {
        CircuitBreaker cb = circuitBreakers.get(serviceName);
        if (cb != null) {
            cb.recordSuccess();
        }
    }

    /**
     * Record a failed call for a service.
     */
    public void recordFailure(String serviceName) {
        CircuitBreaker cb = circuitBreakers.get(serviceName);
        if (cb != null) {
            cb.recordFailure();
        }
    }

    /**
     * Get the circuit breaker for a service.
     */
    public CircuitBreaker getCircuitBreaker(String serviceName) {
        return circuitBreakers.get(serviceName);
    }

    /**
     * Check if a service requires VPN access.
     */
    public boolean requiresVpn(String serviceName) {
        return vpnRequiredServices.getOrDefault(serviceName, false);
    }

    /**
     * Get the status of a service.
     */
    public ServiceStatus getServiceStatus(String serviceName) {
        CircuitBreaker cb = circuitBreakers.get(serviceName);
        if (cb == null) {
            return ServiceStatus.AVAILABLE;
        }
        return cb.getServiceStatus();
    }

    /**
     * Reset all circuit breakers (for testing/manual recovery).
     */
    public void resetAll() {
        circuitBreakers.values().forEach(CircuitBreaker::reset);
        Log.info("All circuit breakers reset");
    }

    /**
     * Convert registry status to JSON.
     */
    public String toJson() {
        if (circuitBreakers.isEmpty()) {
            return "{}";
        }

        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, CircuitBreaker> entry : circuitBreakers.entrySet()) {
            if (!first) sb.append(",");
            first = false;
            sb.append("\"").append(entry.getKey()).append("\":");
            sb.append(entry.getValue().toJson());
        }
        sb.append("}");
        return sb.toString();
    }
}
