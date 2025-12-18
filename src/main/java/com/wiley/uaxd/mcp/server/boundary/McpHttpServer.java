package com.wiley.uaxd.mcp.server.boundary;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.wiley.uaxd.mcp.log.boundary.Log;
import com.wiley.uaxd.mcp.server.control.ApiKeyAuthenticator;
import com.wiley.uaxd.mcp.server.control.HttpRequestHandler;
import com.wiley.uaxd.mcp.server.entity.ServerConfig;
import com.wiley.uaxd.mcp.reliability.boundary.ServiceRegistry;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

/**
 * MCP HTTP Server using Java's built-in HttpServer with virtual threads.
 */
public class McpHttpServer {

    private final ServerConfig config;
    private final ApiKeyAuthenticator authenticator;
    private final HttpRequestHandler requestHandler;
    private final ServiceRegistry serviceRegistry;
    private final AtomicLong requestCounter = new AtomicLong(0);
    private HttpServer server;
    private final Instant startTime = Instant.now();

    public McpHttpServer(ServerConfig config, ServiceRegistry serviceRegistry) {
        this.config = config;
        this.serviceRegistry = serviceRegistry;
        this.authenticator = new ApiKeyAuthenticator(config);
        this.requestHandler = new HttpRequestHandler(serviceRegistry);
    }

    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(config.port()), config.backlog());

        // Use virtual threads for high concurrency
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());

        // Main MCP endpoint
        server.createContext("/mcp", this::handleMcpRequest);

        // Health check endpoint (no auth required)
        server.createContext("/mcp/health", this::handleHealthCheck);

        // Status endpoint (no auth required)
        server.createContext("/mcp/status", this::handleStatus);

        server.start();

        Log.info("MCP HTTP Server started on port " + config.port());
        Log.info("API Key authentication: " + (authenticator.isEnabled() ? "ENABLED" : "DISABLED"));
        Log.info("Virtual threads: ENABLED");
        Log.info("Endpoints:");
        Log.info("  POST /mcp - JSON-RPC requests");
        Log.info("  GET  /mcp/health - Health check");
        Log.info("  GET  /mcp/status - Service status");
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
            Log.info("MCP HTTP Server stopped");
        }
    }

    private void handleMcpRequest(HttpExchange exchange) throws IOException {
        long reqId = requestCounter.incrementAndGet();
        Log.info("[Req#" + reqId + "] " + exchange.getRequestMethod() + " /mcp from " +
                 exchange.getRemoteAddress());

        try {
            // Handle CORS preflight
            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                addCorsHeaders(exchange);
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            addCorsHeaders(exchange);

            // Authenticate
            if (!authenticator.authenticate(exchange)) {
                Log.info("[Req#" + reqId + "] Unauthorized - invalid or missing API key");
                sendUnauthorized(exchange);
                return;
            }

            // Only accept POST for JSON-RPC
            if (!"POST".equals(exchange.getRequestMethod())) {
                sendError(exchange, 405, "Method Not Allowed");
                return;
            }

            // Read request body
            String requestBody;
            try (InputStream is = exchange.getRequestBody()) {
                requestBody = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            }

            // Process the JSON-RPC request
            requestHandler.handleRequest(exchange, requestBody);

        } catch (Exception e) {
            Log.error("[Req#" + reqId + "] Error", e);
            sendError(exchange, 500, "Internal Server Error");
        } finally {
            exchange.close();
        }
    }

    private void handleHealthCheck(HttpExchange exchange) throws IOException {
        addCorsHeaders(exchange);

        if ("OPTIONS".equals(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
            return;
        }

        String response = "{\"status\":\"healthy\",\"service\":\"uaxd-mcp\",\"version\":\"1.0.0\"}";
        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
        exchange.close();
    }

    private void handleStatus(HttpExchange exchange) throws IOException {
        addCorsHeaders(exchange);

        if ("OPTIONS".equals(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
            return;
        }

        long uptime = Instant.now().getEpochSecond() - startTime.getEpochSecond();
        String serviceStatuses = serviceRegistry != null ? serviceRegistry.toJson() : "{}";

        String response = String.format("""
            {
                "service": "uaxd-mcp",
                "version": "1.0.0",
                "uptime_seconds": %d,
                "requests_processed": %d,
                "virtual_threads": true,
                "services": %s
            }""", uptime, requestCounter.get(), serviceStatuses);

        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
        exchange.close();
    }

    private void addCorsHeaders(HttpExchange exchange) {
        var headers = exchange.getResponseHeaders();
        headers.set("Access-Control-Allow-Origin", "*");
        headers.set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        headers.set("Access-Control-Allow-Headers", "Content-Type, Accept, X-API-Key, Mcp-Session-Id");
    }

    private void sendUnauthorized(HttpExchange exchange) throws IOException {
        String response = "{\"jsonrpc\":\"2.0\",\"error\":{\"code\":-32001,\"message\":\"Unauthorized: Invalid or missing API key\"},\"id\":null}";
        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(401, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private void sendError(HttpExchange exchange, int statusCode, String message) throws IOException {
        String response = "{\"error\":\"" + message + "\"}";
        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}
