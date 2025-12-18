package com.wiley.uaxd.mcp.base.boundary;

import com.wiley.uaxd.mcp.base.control.MessageSender;
import com.wiley.uaxd.mcp.log.boundary.Log;
import com.wiley.uaxd.mcp.router.boundary.RequestHandler;
import com.wiley.uaxd.mcp.router.entity.Capability;
import com.wiley.uaxd.mcp.router.entity.MCPRequest;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Handles MCP initialization protocol.
 */
public class CoreSTDIOProtocol implements RequestHandler {

    private static final String PROTOCOL_VERSION = "2024-11-05";
    private static final String SERVER_NAME = "uaxd-mcp";
    private static final String SERVER_VERSION = "1.0.0";

    private final MessageSender sender;
    private final Supplier<List<Capability>> capabilitiesSupplier;
    private boolean initialized = false;

    public CoreSTDIOProtocol(MessageSender sender, Supplier<List<Capability>> capabilitiesSupplier) {
        this.sender = sender;
        this.capabilitiesSupplier = capabilitiesSupplier;
    }

    public boolean isInitialized() {
        return initialized;
    }

    @Override
    public boolean handleRequest(MCPRequest request) {
        return switch (request.method()) {
            case "initialize" -> handleInitialize(request);
            case "initialized" -> handleInitialized(request);
            case "ping" -> handlePing(request);
            default -> false;
        };
    }

    private boolean handleInitialize(MCPRequest request) {
        Log.info("Handling initialize request");

        StringBuilder capabilities = new StringBuilder("{");
        List<Capability> caps = capabilitiesSupplier.get();
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

        sender.sendSuccess(request.id(), result);
        return true;
    }

    private boolean handleInitialized(MCPRequest request) {
        Log.info("Client initialized notification received");
        initialized = true;
        // No response for notifications
        return true;
    }

    private boolean handlePing(MCPRequest request) {
        sender.sendSuccess(request.id(), "{}");
        return true;
    }

    @Override
    public Optional<Capability> capability() {
        return Optional.empty();
    }
}
