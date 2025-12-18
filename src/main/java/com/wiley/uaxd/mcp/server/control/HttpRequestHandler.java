package com.wiley.uaxd.mcp.server.control;

import com.sun.net.httpserver.HttpExchange;
import com.wiley.uaxd.mcp.jsonrpc.entity.JsonParser;
import com.wiley.uaxd.mcp.jsonrpc.entity.JsonRPCResponses;
import com.wiley.uaxd.mcp.log.boundary.Log;
import com.wiley.uaxd.mcp.router.entity.Capability;
import com.wiley.uaxd.mcp.tools.control.ToolInstance;
import com.wiley.uaxd.mcp.tools.control.ToolLocator;
import com.wiley.uaxd.mcp.reliability.boundary.ServiceRegistry;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Handles HTTP JSON-RPC requests for MCP protocol.
 */
public class HttpRequestHandler {

    private static final String PROTOCOL_VERSION = "2024-11-05";
    private static final String SERVER_NAME = "uaxd-mcp";
    private static final String SERVER_VERSION = "1.0.0";

    private final ServiceRegistry serviceRegistry;
    private List<ToolInstance> tools;

    public HttpRequestHandler(ServiceRegistry serviceRegistry) {
        this.serviceRegistry = serviceRegistry;
    }

    private List<ToolInstance> getTools() {
        if (tools == null) {
            tools = ToolLocator.all();
            Log.info("HTTP handler loaded " + tools.size() + " tools");
        }
        return tools;
    }

    public void handleRequest(HttpExchange exchange, String requestBody) throws IOException {
        Log.request("[HTTP] " + requestBody);

        try {
            Map<String, Object> json = JsonParser.parseObject(requestBody);
            if (json.isEmpty()) {
                sendResponse(exchange, 200, JsonRPCResponses.error(null, -32700, "Parse error"));
                return;
            }

            String jsonrpc = (String) json.get("jsonrpc");
            if (!"2.0".equals(jsonrpc)) {
                sendResponse(exchange, 200, JsonRPCResponses.error(json.get("id"), -32600, "Invalid Request"));
                return;
            }

            String method = (String) json.get("method");
            if (method == null || method.isBlank()) {
                sendResponse(exchange, 200, JsonRPCResponses.error(json.get("id"), -32600, "Invalid Request"));
                return;
            }

            Object id = json.get("id");
            @SuppressWarnings("unchecked")
            Map<String, Object> params = (Map<String, Object>) json.get("params");

            String response = processMethod(id, method, params);
            sendResponse(exchange, 200, response);

        } catch (Exception e) {
            Log.error("HTTP handler error", e);
            sendResponse(exchange, 200, JsonRPCResponses.error(null, -32603, "Internal error: " + e.getMessage()));
        }
    }

    private String processMethod(Object id, String method, Map<String, Object> params) {
        return switch (method) {
            case "initialize" -> handleInitialize(id);
            case "initialized" -> null; // No response for notifications
            case "ping" -> JsonRPCResponses.success(id, "{}");
            case "tools/list" -> handleListTools(id);
            case "tools/call" -> handleCallTool(id, params);
            default -> JsonRPCResponses.error(id, -32601, "Method not found: " + method);
        };
    }

    private String handleInitialize(Object id) {
        Log.info("[HTTP] Handling initialize request");

        List<Capability> caps = List.of(Capability.of("tools", true));
        StringBuilder capabilities = new StringBuilder("{");
        for (int i = 0; i < caps.size(); i++) {
            if (i > 0) capabilities.append(",");
            Capability cap = caps.get(i);
            capabilities.append("\"").append(cap.name()).append("\":{");
            if (cap.listChanged()) {
                capabilities.append("\"listChanged\":true");
            }
            capabilities.append("}");
        }
        capabilities.append("}");

        String result = String.format("""
            {
                "protocolVersion": "%s",
                "capabilities": %s,
                "serverInfo": {
                    "name": "%s",
                    "version": "%s"
                }
            }""", PROTOCOL_VERSION, capabilities, SERVER_NAME, SERVER_VERSION);

        return JsonRPCResponses.success(id, result);
    }

    private String handleListTools(Object id) {
        Log.info("[HTTP] Handling tools/list request");

        StringBuilder toolsJson = new StringBuilder("[");
        List<ToolInstance> toolList = getTools();
        for (int i = 0; i < toolList.size(); i++) {
            if (i > 0) toolsJson.append(",");
            toolsJson.append(toolList.get(i).spec().toJson());
        }
        toolsJson.append("]");

        String result = String.format("{\"tools\":%s}", toolsJson);
        return JsonRPCResponses.success(id, result);
    }

    @SuppressWarnings("unchecked")
    private String handleCallTool(Object id, Map<String, Object> params) {
        Log.info("[HTTP] Handling tools/call request");

        if (params == null) {
            return JsonRPCResponses.error(id, -32602, "Invalid params: Missing params");
        }

        String toolName = (String) params.get("name");
        if (toolName == null || toolName.isBlank()) {
            return JsonRPCResponses.error(id, -32602, "Invalid params: Missing tool name");
        }

        Map<String, Object> arguments = (Map<String, Object>) params.get("arguments");
        if (arguments == null) {
            arguments = Map.of();
        }

        Optional<ToolInstance> toolOpt = getTools().stream()
            .filter(t -> t.name().equals(toolName))
            .findFirst();

        if (toolOpt.isEmpty()) {
            return JsonRPCResponses.error(id, -32601, "Tool not found: " + toolName);
        }

        ToolInstance tool = toolOpt.get();

        // Check service availability via circuit breaker
        if (serviceRegistry != null && !serviceRegistry.isServiceAvailable(toolName)) {
            String unavailableResult = formatToolResult(
                "Service temporarily unavailable. This tool requires VPN access to internal Wiley services. " +
                "The service will be retried automatically when connectivity is restored.",
                true
            );
            return JsonRPCResponses.success(id, unavailableResult);
        }

        try {
            Map<String, String> result = tool.execute(arguments);
            String content = result.getOrDefault("content", "");
            boolean isError = "true".equals(result.get("error"));

            // Track failures for circuit breaker
            if (isError && serviceRegistry != null) {
                serviceRegistry.recordFailure(toolName);
            } else if (serviceRegistry != null) {
                serviceRegistry.recordSuccess(toolName);
            }

            String resultJson = formatToolResult(content, isError);
            return JsonRPCResponses.success(id, resultJson);
        } catch (Exception e) {
            Log.error("Tool execution error: " + toolName, e);
            if (serviceRegistry != null) {
                serviceRegistry.recordFailure(toolName);
            }
            String errorResult = formatToolResult("Error executing tool: " + e.getMessage(), true);
            return JsonRPCResponses.success(id, errorResult);
        }
    }

    private String formatToolResult(String content, boolean isError) {
        String escapedContent = escapeJson(content);
        if (isError) {
            return String.format("""
                {"content":[{"type":"text","text":"%s"}],"isError":true}""", escapedContent);
        }
        return String.format("""
            {"content":[{"type":"text","text":"%s"}]}""", escapedContent);
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private void sendResponse(HttpExchange exchange, int statusCode, String body) throws IOException {
        if (body == null) {
            exchange.sendResponseHeaders(204, -1);
            return;
        }

        Log.response("[HTTP] " + body);
        byte[] responseBytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(statusCode, responseBytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(responseBytes);
        }
    }
}
