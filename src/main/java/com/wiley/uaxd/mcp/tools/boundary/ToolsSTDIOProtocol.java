package com.wiley.uaxd.mcp.tools.boundary;

import com.wiley.uaxd.mcp.base.control.MessageSender;
import com.wiley.uaxd.mcp.jsonrpc.entity.JsonParser;
import com.wiley.uaxd.mcp.log.boundary.Log;
import com.wiley.uaxd.mcp.router.boundary.RequestHandler;
import com.wiley.uaxd.mcp.router.entity.Capability;
import com.wiley.uaxd.mcp.router.entity.MCPRequest;
import com.wiley.uaxd.mcp.tools.control.ToolInstance;
import com.wiley.uaxd.mcp.tools.control.ToolLocator;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Handles MCP tools/list and tools/call methods.
 */
public class ToolsSTDIOProtocol implements RequestHandler {

    private final MessageSender sender;
    private List<ToolInstance> tools;

    public ToolsSTDIOProtocol(MessageSender sender) {
        this.sender = sender;
    }

    private List<ToolInstance> getTools() {
        if (tools == null) {
            tools = ToolLocator.all();
            Log.info("Loaded " + tools.size() + " tools");
        }
        return tools;
    }

    @Override
    public boolean handleRequest(MCPRequest request) {
        return switch (request.method()) {
            case "tools/list" -> handleListTools(request);
            case "tools/call" -> handleCallTool(request);
            default -> false;
        };
    }

    private boolean handleListTools(MCPRequest request) {
        Log.info("Handling tools/list request");

        StringBuilder toolsJson = new StringBuilder("[");
        List<ToolInstance> toolList = getTools();
        for (int i = 0; i < toolList.size(); i++) {
            if (i > 0) toolsJson.append(",");
            toolsJson.append(toolList.get(i).spec().toJson());
        }
        toolsJson.append("]");

        String result = String.format("{\"tools\":%s}", toolsJson);
        sender.sendSuccess(request.id(), result);
        return true;
    }

    @SuppressWarnings("unchecked")
    private boolean handleCallTool(MCPRequest request) {
        Log.info("Handling tools/call request");

        Map<String, Object> params = request.params();
        if (params == null) {
            sender.sendInvalidParams(request.id(), "Missing params");
            return true;
        }

        String toolName = (String) params.get("name");
        if (toolName == null || toolName.isBlank()) {
            sender.sendInvalidParams(request.id(), "Missing tool name");
            return true;
        }

        Map<String, Object> arguments = (Map<String, Object>) params.get("arguments");
        if (arguments == null) {
            arguments = Map.of();
        }

        Optional<ToolInstance> toolOpt = getTools().stream()
            .filter(t -> t.name().equals(toolName))
            .findFirst();

        if (toolOpt.isEmpty()) {
            sender.sendError(request.id(), -32601, "Tool not found: " + toolName);
            return true;
        }

        ToolInstance tool = toolOpt.get();
        Map<String, String> result = tool.execute(arguments);

        String content = result.getOrDefault("content", "");
        boolean isError = "true".equals(result.get("error"));

        String resultJson = formatToolResult(content, isError);
        sender.sendSuccess(request.id(), resultJson);
        return true;
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

    @Override
    public Optional<Capability> capability() {
        return Optional.of(Capability.of("tools", true));
    }
}
