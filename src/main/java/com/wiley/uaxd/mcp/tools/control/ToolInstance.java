package com.wiley.uaxd.mcp.tools.control;

import com.wiley.uaxd.mcp.log.boundary.Log;
import com.wiley.uaxd.mcp.tools.entity.ToolSpec;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.function.Function;

/**
 * Wraps a tool function with its metadata.
 */
public record ToolInstance(
    Function<Map<String, Object>, Map<String, String>> tool,
    ToolSpec spec
) {
    @SuppressWarnings("unchecked")
    public static ToolInstance of(Function<Map<String, Object>, Map<String, String>> tool) {
        try {
            Field specField = tool.getClass().getDeclaredField("TOOL_SPEC");
            specField.setAccessible(true);
            Object value = specField.get(null);
            if (value instanceof Map map) {
                return new ToolInstance(tool, ToolSpec.of((Map<String, String>) map));
            }
        } catch (NoSuchFieldException | IllegalAccessException e) {
            Log.error("Failed to read TOOL_SPEC from tool: " + tool.getClass().getName(), e);
        }
        // Fallback
        String name = tool.getClass().getSimpleName();
        return new ToolInstance(tool, ToolSpec.of(name, "No description", null));
    }

    public String name() {
        return spec.name();
    }

    public Map<String, String> execute(Map<String, Object> params) {
        Log.info("Executing tool: " + spec.name());
        try {
            return tool.apply(params);
        } catch (Exception e) {
            Log.error("Tool execution failed: " + spec.name(), e);
            return Map.of(
                "content", "Error executing tool: " + e.getMessage(),
                "error", "true"
            );
        }
    }
}
