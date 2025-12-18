package com.wiley.uaxd.mcp.auth.boundary;

import com.wiley.uaxd.mcp.auth.control.OAuthClient;
import com.wiley.uaxd.mcp.auth.control.WppAuthClient;
import com.wiley.uaxd.mcp.auth.entity.TokenCache;
import com.wiley.uaxd.mcp.log.boundary.Log;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Central token management with caching and auto-refresh.
 */
public class TokenManager {

    private static final String WPP_CACHE_KEY = "wpp";

    private static TokenManager instance;

    private final ConcurrentHashMap<String, TokenCache> tokenCaches = new ConcurrentHashMap<>();
    private final ReentrantLock wppLock = new ReentrantLock();
    private final ConcurrentHashMap<String, ReentrantLock> oauthLocks = new ConcurrentHashMap<>();

    private final WppAuthClient wppAuthClient;
    private final OAuthClient oAuthClient;

    public TokenManager() {
        this.wppAuthClient = new WppAuthClient();
        this.oAuthClient = new OAuthClient();
    }

    public TokenManager(WppAuthClient wppAuthClient, OAuthClient oAuthClient) {
        this.wppAuthClient = wppAuthClient;
        this.oAuthClient = oAuthClient;
    }

    /**
     * Get singleton instance.
     */
    public static synchronized TokenManager getInstance() {
        if (instance == null) {
            instance = new TokenManager();
        }
        return instance;
    }

    /**
     * Gets a WPP auth token, using cache if available.
     */
    public String getWppToken() {
        TokenCache cached = tokenCaches.get(WPP_CACHE_KEY);
        if (cached != null && !cached.shouldRefresh()) {
            Log.info("Using cached WPP token");
            return cached.token();
        }

        wppLock.lock();
        try {
            // Double-check after acquiring lock
            cached = tokenCaches.get(WPP_CACHE_KEY);
            if (cached != null && !cached.shouldRefresh()) {
                return cached.token();
            }

            Log.info("Refreshing WPP token");
            String token = wppAuthClient.authenticate();
            tokenCaches.put(WPP_CACHE_KEY, TokenCache.of(token, WPP_CACHE_KEY));
            return token;
        } finally {
            wppLock.unlock();
        }
    }

    /**
     * Forces refresh of WPP token (call on 401).
     */
    public String refreshWppToken() {
        wppLock.lock();
        try {
            Log.info("Force refreshing WPP token");
            tokenCaches.remove(WPP_CACHE_KEY);
            String token = wppAuthClient.authenticate();
            tokenCaches.put(WPP_CACHE_KEY, TokenCache.of(token, WPP_CACHE_KEY));
            return token;
        } finally {
            wppLock.unlock();
        }
    }

    /**
     * Gets an OAuth token for the specified client, using cache if available.
     */
    public String getOAuthToken(String tokenUrl, String clientId, String clientSecret) {
        String cacheKey = "oauth:" + clientId + "@" + tokenUrl;

        TokenCache cached = tokenCaches.get(cacheKey);
        if (cached != null && !cached.shouldRefresh()) {
            Log.info("Using cached OAuth token for " + clientId);
            return cached.token();
        }

        ReentrantLock lock = oauthLocks.computeIfAbsent(cacheKey, k -> new ReentrantLock());
        lock.lock();
        try {
            // Double-check after acquiring lock
            cached = tokenCaches.get(cacheKey);
            if (cached != null && !cached.shouldRefresh()) {
                return cached.token();
            }

            Log.info("Refreshing OAuth token for " + clientId);
            OAuthClient.OAuthToken result = oAuthClient.getToken(tokenUrl, clientId, clientSecret);
            tokenCaches.put(cacheKey, TokenCache.of(result.accessToken(), result.expiresIn(), cacheKey));
            return result.accessToken();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Forces refresh of OAuth token (call on 401).
     */
    public String refreshOAuthToken(String tokenUrl, String clientId, String clientSecret) {
        String cacheKey = "oauth:" + clientId + "@" + tokenUrl;

        ReentrantLock lock = oauthLocks.computeIfAbsent(cacheKey, k -> new ReentrantLock());
        lock.lock();
        try {
            Log.info("Force refreshing OAuth token for " + clientId);
            tokenCaches.remove(cacheKey);
            OAuthClient.OAuthToken result = oAuthClient.getToken(tokenUrl, clientId, clientSecret);
            tokenCaches.put(cacheKey, TokenCache.of(result.accessToken(), result.expiresIn(), cacheKey));
            return result.accessToken();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Clears all cached tokens.
     */
    public void clearAll() {
        tokenCaches.clear();
        Log.info("All cached tokens cleared");
    }
}
