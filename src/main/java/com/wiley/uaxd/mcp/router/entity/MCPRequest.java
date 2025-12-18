package com.wiley.uaxd.mcp.router.entity;

import java.util.Map;

/**
 * Represents a parsed MCP JSON-RPC request.
 */
public record MCPRequest(
    Object id,
    String method,
    Map<String, Object> params,
    String rawJson
) {
    public boolean hasId() {
        return id != null;
    }

    public int idAsInt() {
        if (id instanceof Number n) {
            return n.intValue();
        }
        return 0;
    }
}
