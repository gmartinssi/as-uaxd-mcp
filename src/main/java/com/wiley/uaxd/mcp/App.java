package com.wiley.uaxd.mcp;

import com.wiley.uaxd.mcp.base.boundary.CoreSTDIOProtocol;
import com.wiley.uaxd.mcp.base.control.MessageSender;
import com.wiley.uaxd.mcp.log.boundary.Log;
import com.wiley.uaxd.mcp.router.boundary.FrontDoor;
import com.wiley.uaxd.mcp.server.boundary.McpHttpServer;
import com.wiley.uaxd.mcp.server.entity.ServerConfig;
import com.wiley.uaxd.mcp.reliability.boundary.ServiceRegistry;
import com.wiley.uaxd.mcp.reliability.control.HealthChecker;
import com.wiley.uaxd.mcp.tools.boundary.ToolsSTDIOProtocol;

/**
 * UAXD MCP Server entry point.
 * Supports both STDIO mode (default) and HTTP mode (--http flag).
 */
public interface App {

    String VERSION = "uaxd-mcp v1.0.0";

    static void main(String[] args) {
        Log.info(VERSION + " starting...");

        boolean httpMode = hasFlag(args, "--http");

        if (httpMode) {
            startHttpServer(args);
        } else {
            startStdioServer();
        }
    }

    private static boolean hasFlag(String[] args, String flag) {
        for (String arg : args) {
            if (flag.equals(arg)) {
                return true;
            }
        }
        return false;
    }

    private static void startHttpServer(String[] args) {
        Log.info("Starting in HTTP mode...");

        try {
            ServerConfig config = ServerConfig.fromArgs(args);
            ServiceRegistry registry = new ServiceRegistry();
            HealthChecker healthChecker = new HealthChecker(registry);

            McpHttpServer server = new McpHttpServer(config, registry);

            // Shutdown hook
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                Log.info("Shutdown signal received");
                healthChecker.stop();
                server.stop();
            }));

            // Start health checker
            healthChecker.start();

            // Start HTTP server (blocks)
            server.start();

            // Keep main thread alive
            Thread.currentThread().join();

        } catch (Exception e) {
            Log.error("Failed to start HTTP server", e);
            System.exit(1);
        }
    }

    private static void startStdioServer() {
        Log.info("Starting in STDIO mode...");

        MessageSender sender = new MessageSender();
        FrontDoor frontDoor = new FrontDoor(sender);

        // Core protocol handler (initialize, ping)
        CoreSTDIOProtocol coreProtocol = new CoreSTDIOProtocol(sender, frontDoor::capabilities);
        frontDoor.addHandler(coreProtocol);

        // Tools handler
        ToolsSTDIOProtocol toolsProtocol = new ToolsSTDIOProtocol(sender);
        frontDoor.addHandler(toolsProtocol);

        // Start the server (blocks on stdin)
        frontDoor.start();
    }
}
