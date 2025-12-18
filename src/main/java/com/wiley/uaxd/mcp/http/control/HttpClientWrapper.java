package com.wiley.uaxd.mcp.http.control;

import com.wiley.uaxd.mcp.log.boundary.Log;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * HTTP client wrapper with common configurations.
 */
public class HttpClientWrapper {

    private final HttpClient client;

    public HttpClientWrapper() {
        this.client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();
    }

    public HttpResponse<String> get(String url, String... headers) throws Exception {
        var builder = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(Duration.ofSeconds(60))
            .GET();

        addHeaders(builder, headers);
        var request = builder.build();

        Log.info("HTTP GET: " + url);
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    public HttpResponse<String> post(String url, String body, String contentType, String... headers) throws Exception {
        var builder = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(Duration.ofSeconds(60))
            .header("Content-Type", contentType)
            .POST(HttpRequest.BodyPublishers.ofString(body));

        addHeaders(builder, headers);
        var request = builder.build();

        Log.info("HTTP POST: " + url);
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    public HttpResponse<String> postForm(String url, String formData, String... headers) throws Exception {
        return post(url, formData, "application/x-www-form-urlencoded", headers);
    }

    public HttpResponse<String> postJson(String url, String jsonBody, String... headers) throws Exception {
        return post(url, jsonBody, "application/json", headers);
    }

    private void addHeaders(HttpRequest.Builder builder, String[] headers) {
        for (int i = 0; i < headers.length - 1; i += 2) {
            builder.header(headers[i], headers[i + 1]);
        }
    }
}
