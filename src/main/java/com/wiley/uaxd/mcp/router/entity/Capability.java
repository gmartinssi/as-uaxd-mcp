package com.wiley.uaxd.mcp.router.entity;

/**
 * Represents an MCP server capability.
 */
public record Capability(String name, boolean listChanged) {

    public static Capability of(String name) {
        return new Capability(name, false);
    }

    public static Capability of(String name, boolean listChanged) {
        return new Capability(name, listChanged);
    }

    public String toJson() {
        if (listChanged) {
            return String.format("{\"%s\": {\"listChanged\": true}}", name);
        }
        return String.format("{\"%s\": {}}", name);
    }
}
