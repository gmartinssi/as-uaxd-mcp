package com.wiley.uaxd.mcp.auth.entity;

import java.time.Instant;

/**
 * Cached authentication token with expiration.
 */
public record TokenCache(
    String token,
    Instant expiresAt,
    String cacheKey
) {
    private static final long REFRESH_THRESHOLD_PERCENT = 80;

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }

    public boolean shouldRefresh() {
        if (isExpired()) return true;
        // Refresh when 80% of TTL has passed
        long totalTtl = expiresAt.toEpochMilli() - (expiresAt.toEpochMilli() - ttlMillis());
        long remaining = expiresAt.toEpochMilli() - Instant.now().toEpochMilli();
        return remaining < (totalTtl * (100 - REFRESH_THRESHOLD_PERCENT) / 100);
    }

    private long ttlMillis() {
        // Estimate original TTL (30 minutes default)
        return 30 * 60 * 1000;
    }

    public static TokenCache of(String token, long ttlSeconds, String cacheKey) {
        Instant expires = Instant.now().plusSeconds(ttlSeconds);
        return new TokenCache(token, expires, cacheKey);
    }

    public static TokenCache of(String token, String cacheKey) {
        // Default 30 minute TTL
        return of(token, 30 * 60, cacheKey);
    }
}
