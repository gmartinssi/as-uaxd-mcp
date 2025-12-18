package com.wiley.uaxd.mcp.tools.control;

import com.wiley.uaxd.mcp.auth.boundary.TokenManager;
import com.wiley.uaxd.mcp.http.control.HttpClientWrapper;
import com.wiley.uaxd.mcp.log.boundary.Log;

import java.net.http.HttpResponse;
import java.util.Map;
import java.util.function.Function;

/**
 * MCP Tool: Retrieves UAXD dashboard articles for a user.
 */
public class GetUAXDArticles implements Function<Map<String, Object>, Map<String, String>> {

    public static final Map<String, String> TOOL_SPEC = Map.of(
        "name", "GetUAXDArticles",
        "description", "Retrieves UAXD dashboard articles for a given user ID. Returns a list of articles associated with the user's UAXD dashboard.",
        "inputSchema", """
            {
                "type": "object",
                "properties": {
                    "userId": {
                        "type": "string",
                        "description": "The user ID (UUID) to retrieve articles for"
                    }
                },
                "required": ["userId"]
            }
            """
    );

    private static final String API_URL =
        "http://host.docker.internal:8080/dashboard/api/v1/mcp/get-articles";

    private final HttpClientWrapper httpClient;
    private final TokenManager tokenManager;

    public GetUAXDArticles() {
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
            Log.error("GetUAXDArticles failed", e);
            return Map.of(
                "content", "Error fetching UAXD articles: " + e.getMessage(),
                "error", "true"
            );
        }
    }

    private Map<String, String> fetchArticles(String userId, boolean isRetry) throws Exception {
        String token = tokenManager.getWppToken();
        String url = API_URL + "?userId=" + userId;

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
        // Return the raw JSON response - Claude can parse it
        return "UAXD Articles:\n" + jsonResponse;
    }
}
