package com.wiley.uaxd.mcp.auth.control;

import com.wiley.uaxd.mcp.http.control.HttpClientWrapper;
import com.wiley.uaxd.mcp.log.boundary.Log;

import java.net.http.HttpResponse;

/**
 * Wiley WPP system authentication client.
 */
public class WppAuthClient {

    private static final String AUTH_URL =
        "http://wpp-auth-svc-wqa.aws.wiley.com:8080/v1/auth/authenticate/system";
    private static final String SYSTEM_ID = "93b3eacd-3187-4ea0-a8e8-ac4aa0f1e24b";
    private static final String SECRET_KEY = "1m7TY7OBpsNpBmp2bYqV7uqExO8jUHPQ";

    private final HttpClientWrapper httpClient;

    public WppAuthClient() {
        this.httpClient = new HttpClientWrapper();
    }

    public WppAuthClient(HttpClientWrapper httpClient) {
        this.httpClient = httpClient;
    }

    /**
     * Authenticates with WPP and returns the auth token.
     * @return X-WPP-AUTH-TOKEN value
     * @throws RuntimeException if authentication fails
     */
    public String authenticate() {
        try {
            String body = String.format(
                "{\"systemId\":\"%s\",\"secretKey\":\"%s\"}",
                SYSTEM_ID, SECRET_KEY
            );

            HttpResponse<String> response = httpClient.postJson(AUTH_URL, body);

            if (response.statusCode() != 200) {
                Log.error("WPP auth failed with status: " + response.statusCode());
                throw new RuntimeException("WPP authentication failed: " + response.statusCode());
            }

            // Token is returned in X-WPP-AUTH-TOKEN header
            String token = response.headers()
                .firstValue("x-wpp-auth-token")
                .orElse(null);

            if (token == null || token.isBlank()) {
                // Try to extract from response body as fallback
                String responseBody = response.body();
                token = extractTokenFromBody(responseBody);
            }

            if (token == null || token.isBlank()) {
                Log.error("No auth token in WPP response");
                throw new RuntimeException("No auth token in WPP response");
            }

            Log.info("WPP authentication successful");
            return token;

        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            Log.error("WPP auth error", e);
            throw new RuntimeException("WPP authentication error: " + e.getMessage(), e);
        }
    }

    private String extractTokenFromBody(String body) {
        // Simple extraction - look for authToken in JSON response
        if (body == null) return null;
        int idx = body.indexOf("\"authToken\"");
        if (idx < 0) {
            idx = body.indexOf("\"token\"");
        }
        if (idx < 0) return null;

        int colonIdx = body.indexOf(":", idx);
        if (colonIdx < 0) return null;

        int startQuote = body.indexOf("\"", colonIdx);
        if (startQuote < 0) return null;

        int endQuote = body.indexOf("\"", startQuote + 1);
        if (endQuote < 0) return null;

        return body.substring(startQuote + 1, endQuote);
    }
}
