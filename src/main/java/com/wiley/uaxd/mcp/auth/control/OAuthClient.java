package com.wiley.uaxd.mcp.auth.control;

import com.wiley.uaxd.mcp.http.control.HttpClientWrapper;
import com.wiley.uaxd.mcp.jsonrpc.entity.JsonParser;
import com.wiley.uaxd.mcp.log.boundary.Log;

import java.net.URLEncoder;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * OAuth2 client_credentials flow client.
 */
public class OAuthClient {

    private final HttpClientWrapper httpClient;

    public OAuthClient() {
        this.httpClient = new HttpClientWrapper();
    }

    public OAuthClient(HttpClientWrapper httpClient) {
        this.httpClient = httpClient;
    }

    /**
     * Result of OAuth token request.
     */
    public record OAuthToken(String accessToken, long expiresIn) {}

    /**
     * Gets an OAuth2 access token using client_credentials grant.
     * @param tokenUrl OAuth token endpoint
     * @param clientId Client ID
     * @param clientSecret Client secret
     * @return OAuthToken with access token and expiration
     */
    public OAuthToken getToken(String tokenUrl, String clientId, String clientSecret) {
        try {
            String formData = String.format(
                "grant_type=%s&client_id=%s&client_secret=%s",
                encode("client_credentials"),
                encode(clientId),
                encode(clientSecret)
            );

            HttpResponse<String> response = httpClient.postForm(tokenUrl, formData);

            if (response.statusCode() != 200) {
                Log.error("OAuth token request failed: " + response.statusCode());
                throw new RuntimeException("OAuth authentication failed: " + response.statusCode());
            }

            String body = response.body();
            Map<String, Object> json = JsonParser.parseObject(body);

            String accessToken = (String) json.get("access_token");
            if (accessToken == null || accessToken.isBlank()) {
                Log.error("No access_token in OAuth response");
                throw new RuntimeException("No access_token in OAuth response");
            }

            long expiresIn = 300; // Default 5 minutes
            Object expiresObj = json.get("expires_in");
            if (expiresObj instanceof Number n) {
                expiresIn = n.longValue();
            }

            Log.info("OAuth authentication successful, expires in " + expiresIn + "s");
            return new OAuthToken(accessToken, expiresIn);

        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            Log.error("OAuth error", e);
            throw new RuntimeException("OAuth authentication error: " + e.getMessage(), e);
        }
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
