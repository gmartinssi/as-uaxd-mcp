package com.wiley.uaxd.mcp.reliability.control;

import com.wiley.uaxd.mcp.log.boundary.Log;
import com.wiley.uaxd.mcp.reliability.entity.CircuitState;
import com.wiley.uaxd.mcp.reliability.entity.ServiceStatus;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * Circuit breaker implementation for fault tolerance.
 * Protects against cascading failures when services are unavailable.
 */
public class CircuitBreaker {

    private final String serviceName;
    private final int failureThreshold;
    private final Duration openDuration;
    private final ReentrantLock lock = new ReentrantLock();

    private CircuitState state = CircuitState.CLOSED;
    private int failureCount = 0;
    private int successCount = 0;
    private Instant lastStateChange = Instant.now();
    private Instant lastFailure = null;

    public CircuitBreaker(String serviceName) {
        this(serviceName, 3, Duration.ofMinutes(1));
    }

    public CircuitBreaker(String serviceName, int failureThreshold, Duration openDuration) {
        this.serviceName = serviceName;
        this.failureThreshold = failureThreshold;
        this.openDuration = openDuration;
    }

    /**
     * Execute an action with circuit breaker protection.
     * @param action the action to execute
     * @param fallback the fallback to return if circuit is open
     * @return the result of the action or fallback
     */
    public <T> T execute(Supplier<T> action, Supplier<T> fallback) {
        if (!isAvailable()) {
            Log.info("Circuit OPEN for " + serviceName + ", returning fallback");
            return fallback.get();
        }

        try {
            T result = action.get();
            recordSuccess();
            return result;
        } catch (Exception e) {
            recordFailure();
            Log.error("Circuit breaker caught exception for " + serviceName, e);
            return fallback.get();
        }
    }

    /**
     * Check if the circuit allows requests through.
     */
    public boolean isAvailable() {
        lock.lock();
        try {
            switch (state) {
                case CLOSED:
                    return true;

                case OPEN:
                    // Check if we should transition to half-open
                    if (Duration.between(lastStateChange, Instant.now()).compareTo(openDuration) >= 0) {
                        transitionTo(CircuitState.HALF_OPEN);
                        return true;
                    }
                    return false;

                case HALF_OPEN:
                    // Allow one request through in half-open state
                    return true;

                default:
                    return true;
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Record a successful call.
     */
    public void recordSuccess() {
        lock.lock();
        try {
            successCount++;
            if (state == CircuitState.HALF_OPEN) {
                // Success in half-open state, close the circuit
                transitionTo(CircuitState.CLOSED);
                failureCount = 0;
            } else if (state == CircuitState.CLOSED) {
                // Reset failure count on success
                failureCount = 0;
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Record a failed call.
     */
    public void recordFailure() {
        lock.lock();
        try {
            failureCount++;
            lastFailure = Instant.now();

            if (state == CircuitState.HALF_OPEN) {
                // Failure in half-open state, reopen the circuit
                transitionTo(CircuitState.OPEN);
            } else if (state == CircuitState.CLOSED && failureCount >= failureThreshold) {
                // Threshold reached, open the circuit
                transitionTo(CircuitState.OPEN);
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Manually reset the circuit breaker.
     */
    public void reset() {
        lock.lock();
        try {
            transitionTo(CircuitState.CLOSED);
            failureCount = 0;
            successCount = 0;
        } finally {
            lock.unlock();
        }
    }

    private void transitionTo(CircuitState newState) {
        if (state != newState) {
            Log.info("Circuit " + serviceName + ": " + state + " -> " + newState);
            state = newState;
            lastStateChange = Instant.now();
        }
    }

    public CircuitState getState() {
        return state;
    }

    public ServiceStatus getServiceStatus() {
        return switch (state) {
            case CLOSED -> failureCount > 0 ? ServiceStatus.DEGRADED : ServiceStatus.AVAILABLE;
            case OPEN -> ServiceStatus.UNAVAILABLE;
            case HALF_OPEN -> ServiceStatus.DEGRADED;
        };
    }

    public String getServiceName() {
        return serviceName;
    }

    public int getFailureCount() {
        return failureCount;
    }

    public int getSuccessCount() {
        return successCount;
    }

    public String toJson() {
        return String.format("""
            {
                "service": "%s",
                "state": "%s",
                "status": "%s",
                "failure_count": %d,
                "success_count": %d,
                "failure_threshold": %d,
                "last_failure": %s
            }""",
            serviceName,
            state.name(),
            getServiceStatus().name(),
            failureCount,
            successCount,
            failureThreshold,
            lastFailure != null ? "\"" + lastFailure + "\"" : "null"
        );
    }
}
