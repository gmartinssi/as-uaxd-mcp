package com.wiley.uaxd.mcp.tools.control;

import com.wiley.uaxd.mcp.log.boundary.Log;

import java.util.*;
import java.util.function.Function;

/**
 * Discovers and loads tools via Java SPI (ServiceLoader).
 */
public interface ToolLocator {

    @SuppressWarnings("unchecked")
    static List<ToolInstance> all() {
        List<ToolInstance> tools = new ArrayList<>();
        ServiceLoader<Function> loader = ServiceLoader.load(Function.class);

        for (Function<?, ?> func : loader) {
            try {
                // Check if it has TOOL_SPEC field
                var specField = func.getClass().getDeclaredField("TOOL_SPEC");
                if (specField != null) {
                    Function<Map<String, Object>, Map<String, String>> tool =
                        (Function<Map<String, Object>, Map<String, String>>) func;
                    tools.add(ToolInstance.of(tool));
                    Log.info("Loaded tool: " + func.getClass().getSimpleName());
                }
            } catch (NoSuchFieldException e) {
                // Not a tool, skip
            } catch (Exception e) {
                Log.error("Failed to load tool: " + func.getClass().getName(), e);
            }
        }
        return tools;
    }

    static Optional<ToolInstance> findTool(String name) {
        return all().stream()
            .filter(t -> t.name().equals(name))
            .findFirst();
    }
}
