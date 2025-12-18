package com.wiley.uaxd.mcp.server.control;

import com.sun.net.httpserver.HttpExchange;
import com.wiley.uaxd.mcp.server.entity.ServerConfig;

/**
 * Authenticates requests using X-API-Key header.
 */
public class ApiKeyAuthenticator {

    private static final String API_KEY_HEADER = "X-API-Key";
    private final String validApiKey;

    public ApiKeyAuthenticator(ServerConfig config) {
        this.validApiKey = config.apiKey();
    }

    public boolean authenticate(HttpExchange exchange) {
        if (validApiKey == null || validApiKey.isBlank()) {
            // No API key configured, allow all requests
            return true;
        }

        String providedKey = exchange.getRequestHeaders().getFirst(API_KEY_HEADER);
        return validApiKey.equals(providedKey);
    }

    public boolean isEnabled() {
        return validApiKey != null && !validApiKey.isBlank();
    }
}
