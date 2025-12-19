package com.wiley.uaxd.mcp.tools.entity;

import java.util.Map;

/**
 * Tool specification with name, description, and input schema.
 */
public record ToolSpec(String name, String description, String inputSchema) {

    public static final String DEFAULT_SCHEMA =
        "{\"type\":\"object\",\"properties\":{\"input\":{\"type\":\"string\"}},\"required\":[\"input\"]}";

    public static ToolSpec of(String name, String description, String inputSchema) {
        return new ToolSpec(name, description, inputSchema != null ? inputSchema : DEFAULT_SCHEMA);
    }

    public static ToolSpec of(Map<String, String> map) {
        return new ToolSpec(
            map.getOrDefault("name", "unknown"),
            map.getOrDefault("description", "No description"),
            map.getOrDefault("inputSchema", DEFAULT_SCHEMA)
        );
    }

    public String toJson() {
        return String.format("{\"name\":\"%s\",\"description\":\"%s\",\"inputSchema\":%s}",
            escapeJson(name), escapeJson(description), inputSchema);
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
