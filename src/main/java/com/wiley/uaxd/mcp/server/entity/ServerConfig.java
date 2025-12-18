package com.wiley.uaxd.mcp.server.entity;

/**
 * HTTP server configuration.
 */
public record ServerConfig(
    int port,
    String apiKey,
    int backlog
) {
    public static final int DEFAULT_PORT = 8478;
    public static final int DEFAULT_BACKLOG = 50;

    public static ServerConfig fromArgs(String[] args) {
        int port = DEFAULT_PORT;
        String apiKey = System.getenv("MCP_API_KEY");

        for (int i = 0; i < args.length; i++) {
            if ("--port".equals(args[i]) && i + 1 < args.length) {
                port = Integer.parseInt(args[i + 1]);
            } else if (args[i].startsWith("--port=")) {
                port = Integer.parseInt(args[i].substring(7));
            }
        }

        return new ServerConfig(port, apiKey, DEFAULT_BACKLOG);
    }

    public boolean hasApiKey() {
        return apiKey != null && !apiKey.isBlank();
    }
}
