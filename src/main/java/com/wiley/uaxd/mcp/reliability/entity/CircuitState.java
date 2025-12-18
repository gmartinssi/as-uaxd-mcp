package com.wiley.uaxd.mcp.reliability.entity;

/**
 * States for the circuit breaker pattern.
 */
public enum CircuitState {
    /** Circuit is closed, requests flow normally */
    CLOSED,

    /** Circuit is open, requests are blocked */
    OPEN,

    /** Circuit is testing, allowing one request through */
    HALF_OPEN
}
