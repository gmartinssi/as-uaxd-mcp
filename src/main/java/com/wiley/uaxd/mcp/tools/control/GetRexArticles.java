package com.wiley.uaxd.mcp.tools.control;

import com.wiley.uaxd.mcp.auth.boundary.TokenManager;
import com.wiley.uaxd.mcp.http.control.HttpClientWrapper;
import com.wiley.uaxd.mcp.log.boundary.Log;

import java.net.http.HttpResponse;
import java.util.Map;
import java.util.function.Function;

/**
 * MCP Tool: Retrieves Rex platform articles for a user.
 */
public class GetRexArticles implements Function<Map<String, Object>, Map<String, String>> {

    public static final Map<String, String> TOOL_SPEC = Map.of(
        "name", "GetRexArticles",
        "description", "Retrieves Rex platform articles for a given user ID. Returns article cards from the Rex/Atypon platform.",
        "inputSchema", "{\"type\":\"object\",\"properties\":{\"userId\":{\"type\":\"string\",\"description\":\"The user ID (UUID) to retrieve articles for\"}},\"required\":[\"userId\"]}"
    );

    private static final String TOKEN_URL =
        "https://auth.uat.nonprod.atyponrex.com/auth/realms/WILEY/protocol/openid-connect/token";
    private static final String CLIENT_ID = "uaxd";
    private static final String CLIENT_SECRET = "iwqL3S7VDIRjJ1PXTZabPOwqoe0J0A7J";
    private static final String TENANT_ID = "0636030c-5229-481c-a745-230521c60957";
    private static final String API_URL_TEMPLATE =
        "https://api.uat.nonprod.atyponrex.com/v1/uaxd/tenants/%s/authors/%s/article-cards/";

    private final HttpClientWrapper httpClient;
    private final TokenManager tokenManager;

    public GetRexArticles() {
        this.httpClient = new HttpClientWrapper();
        this.tokenManager = TokenManager.getInstance();
    }

    @Override
    public Map<String, String> apply(Map<String, Object> params) {
        String userId = (String) params.get("userId");
        if (userId == null || userId.isBlank()) {
            return Map.of(
                "content", "Error: userId parameter is required",
                "error", "true"
            );
        }

        try {
            return fetchArticles(userId, false);
        } catch (Exception e) {
            Log.error("GetRexArticles failed", e);
            return Map.of(
                "content", "Error fetching Rex articles: " + e.getMessage(),
                "error", "true"
            );
        }
    }

    private Map<String, String> fetchArticles(String userId, boolean isRetry) throws Exception {
        String token = tokenManager.getOAuthToken(TOKEN_URL, CLIENT_ID, CLIENT_SECRET);
        String url = String.format(API_URL_TEMPLATE, TENANT_ID, userId);

        HttpResponse<String> response = httpClient.get(url,
            "Authorization", "Bearer " + token
        );

        if (response.statusCode() == 401 && !isRetry) {
            Log.info("Got 401, refreshing OAuth token and retrying");
            tokenManager.refreshOAuthToken(TOKEN_URL, CLIENT_ID, CLIENT_SECRET);
            return fetchArticles(userId, true);
        }

        if (response.statusCode() != 200) {
            return Map.of(
                "content", "API returned status " + response.statusCode() + ": " + response.body(),
                "error", "true"
            );
        }

        String body = response.body();
        return Map.of(
            "content", formatResponse(body),
            "error", "false"
        );
    }

    private String formatResponse(String jsonResponse) {
        return "Rex Articles:\n" + jsonResponse;
    }
}
