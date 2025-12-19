package com.wiley.uaxd.mcp.tools.control;

import com.wiley.uaxd.mcp.auth.boundary.TokenManager;
import com.wiley.uaxd.mcp.http.control.HttpClientWrapper;
import com.wiley.uaxd.mcp.log.boundary.Log;

import java.net.http.HttpResponse;
import java.util.Map;
import java.util.function.Function;

/**
 * MCP Tool: Retrieves Author Services (AS) articles for a user.
 */
public class GetASArticles implements Function<Map<String, Object>, Map<String, String>> {

    public static final Map<String, String> TOOL_SPEC = Map.of(
        "name", "GetASArticles",
        "description", "Retrieves Author Services (AS) articles for a given user ID. Returns article cards from the AS platform.",
        "inputSchema", "{\"type\":\"object\",\"properties\":{\"userId\":{\"type\":\"string\",\"description\":\"The user ID (UUID) to retrieve articles for\"}},\"required\":[\"userId\"]}"
    );

    private static final String TENANT_ID = "0636030c-5229-481c-a745-230521c60957";
    private static final String API_URL_TEMPLATE =
        "http://as-app-wqa.aws.wiley.com:8080/v1/uaxd/tenants/%s/authors/%s/article-cards";

    private final HttpClientWrapper httpClient;
    private final TokenManager tokenManager;

    public GetASArticles() {
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
            Log.error("GetASArticles failed", e);
            return Map.of(
                "content", "Error fetching AS articles: " + e.getMessage(),
                "error", "true"
            );
        }
    }

    private Map<String, String> fetchArticles(String userId, boolean isRetry) throws Exception {
        String token = tokenManager.getWppToken();
        String url = String.format(API_URL_TEMPLATE, TENANT_ID, userId);

        HttpResponse<String> response = httpClient.get(url,
            "X-WPP-AUTH-TOKEN", token
        );

        if (response.statusCode() == 401 && !isRetry) {
            Log.info("Got 401, refreshing token and retrying");
            tokenManager.refreshWppToken();
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
        return "Author Services Articles:\n" + jsonResponse;
    }
}
