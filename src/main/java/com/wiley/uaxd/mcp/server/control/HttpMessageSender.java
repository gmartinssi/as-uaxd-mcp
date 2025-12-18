package com.wiley.uaxd.mcp.server.control;

import com.wiley.uaxd.mcp.jsonrpc.entity.JsonRPCResponses;
import com.wiley.uaxd.mcp.log.boundary.Log;

import java.util.function.Consumer;

/**
 * Sends JSON-RPC messages via HTTP response callback.
 */
public class HttpMessageSender {

    private Consumer<String> responseConsumer;

    public void setResponseConsumer(Consumer<String> consumer) {
        this.responseConsumer = consumer;
    }

    public void send(String json) {
        Log.response(json);
        if (responseConsumer != null) {
            responseConsumer.accept(json);
        }
    }

    public void sendSuccess(Object id, String resultJson) {
        send(JsonRPCResponses.success(id, resultJson));
    }

    public void sendError(Object id, int code, String message) {
        send(JsonRPCResponses.error(id, code, message));
    }

    public void sendParseError(Object id) {
        sendError(id, -32700, "Parse error");
    }

    public void sendInvalidRequest(Object id) {
        sendError(id, -32600, "Invalid Request");
    }

    public void sendMethodNotFound(Object id, String method) {
        sendError(id, -32601, "Method not found: " + method);
    }

    public void sendInvalidParams(Object id, String message) {
        sendError(id, -32602, "Invalid params: " + message);
    }

    public void sendInternalError(Object id, String message) {
        sendError(id, -32603, "Internal error: " + message);
    }

    public void sendUnauthorized() {
        send("{\"jsonrpc\":\"2.0\",\"error\":{\"code\":-32001,\"message\":\"Unauthorized: Invalid or missing API key\"},\"id\":null}");
    }
}
