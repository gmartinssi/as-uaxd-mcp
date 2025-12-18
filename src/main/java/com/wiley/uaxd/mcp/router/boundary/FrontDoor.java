package com.wiley.uaxd.mcp.router.boundary;

import com.wiley.uaxd.mcp.base.control.MessageSender;
import com.wiley.uaxd.mcp.jsonrpc.entity.JsonParser;
import com.wiley.uaxd.mcp.log.boundary.Log;
import com.wiley.uaxd.mcp.router.entity.Capability;
import com.wiley.uaxd.mcp.router.entity.MCPRequest;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;

/**
 * Main router that dispatches JSON-RPC requests to handlers.
 */
public class FrontDoor {

    private final MessageSender sender;
    private final List<RequestHandler> handlers = new ArrayList<>();

    public FrontDoor(MessageSender sender) {
        this.sender = sender;
    }

    public FrontDoor addHandler(RequestHandler handler) {
        handlers.add(handler);
        return this;
    }

    public List<Capability> capabilities() {
        return handlers.stream()
            .map(RequestHandler::capability)
            .filter(Optional::isPresent)
            .map(Optional::get)
            .toList();
    }

    public void start() {
        Log.info("UAXD MCP Server starting...");
        Log.info("Registered " + handlers.size() + " handlers");

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) continue;
                processLine(line);
            }
        } catch (IOException e) {
            Log.error("Error reading stdin", e);
        }

        Log.info("UAXD MCP Server stopped");
    }

    private void processLine(String line) {
        Log.request(line);

        try {
            Map<String, Object> json = JsonParser.parseObject(line);
            if (json.isEmpty()) {
                sender.sendParseError(null);
                return;
            }

            // Validate JSON-RPC format
            String jsonrpc = (String) json.get("jsonrpc");
            if (!"2.0".equals(jsonrpc)) {
                sender.sendInvalidRequest(json.get("id"));
                return;
            }

            String method = (String) json.get("method");
            if (method == null || method.isBlank()) {
                sender.sendInvalidRequest(json.get("id"));
                return;
            }

            Object id = json.get("id");
            @SuppressWarnings("unchecked")
            Map<String, Object> params = (Map<String, Object>) json.get("params");

            MCPRequest request = new MCPRequest(id, method, params, line);
            dispatchRequest(request);

        } catch (Exception e) {
            Log.error("Error processing request", e);
            sender.sendInternalError(null, e.getMessage());
        }
    }

    private void dispatchRequest(MCPRequest request) {
        for (RequestHandler handler : handlers) {
            try {
                if (handler.handleRequest(request)) {
                    return; // Request was handled
                }
            } catch (Exception e) {
                Log.error("Handler error: " + handler.getClass().getSimpleName(), e);
                sender.sendInternalError(request.id(), e.getMessage());
                return;
            }
        }

        // No handler found
        sender.sendMethodNotFound(request.id(), request.method());
    }
}
