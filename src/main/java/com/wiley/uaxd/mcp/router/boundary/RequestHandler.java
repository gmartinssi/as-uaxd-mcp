package com.wiley.uaxd.mcp.router.boundary;

import com.wiley.uaxd.mcp.router.entity.Capability;
import com.wiley.uaxd.mcp.router.entity.MCPRequest;

import java.util.Optional;

/**
 * Interface for MCP protocol handlers.
 */
public interface RequestHandler {

    /**
     * Handles an MCP request.
     * @param request the parsed request
     * @return true if the request was handled, false to pass to next handler
     */
    boolean handleRequest(MCPRequest request);

    /**
     * Returns the capability this handler provides, if any.
     */
    default Optional<Capability> capability() {
        return Optional.empty();
    }
}
