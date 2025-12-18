package com.wiley.uaxd.mcp.base.control;

import com.wiley.uaxd.mcp.jsonrpc.entity.JsonRPCResponses;
import com.wiley.uaxd.mcp.log.boundary.Log;

import java.io.PrintWriter;

/**
 * Sends JSON-RPC messages to stdout.
 */
public class MessageSender {
    private final PrintWriter out;

    public MessageSender() {
        this.out = new PrintWriter(System.out, true);
    }

    public void send(String json) {
        Log.response(json);
        out.println(json);
        out.flush();
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
}
