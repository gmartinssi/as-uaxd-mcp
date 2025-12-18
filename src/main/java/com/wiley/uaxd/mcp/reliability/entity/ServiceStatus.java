package com.wiley.uaxd.mcp.reliability.entity;

/**
 * Service availability status.
 */
public enum ServiceStatus {
    /** Service is available and responding */
    AVAILABLE,

    /** Service is unavailable (circuit open) */
    UNAVAILABLE,

    /** Service is degraded (some failures) */
    DEGRADED
}
