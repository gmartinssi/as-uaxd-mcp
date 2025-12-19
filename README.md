# AS-UAXD MCP Server

A Model Context Protocol (MCP) server for accessing UAXD (Unified Author Experience Dashboard) articles and related services. Built with Java 25 and virtual threads for high-performance concurrent request handling.

## Features

- **Dual Transport Support**: STDIO mode for local Claude Code integration, HTTP mode for remote access
- **Virtual Threads**: Java 25 Project Loom for efficient concurrency
- **Circuit Breaker Pattern**: Fault tolerance for VPN-dependent services
- **API Key Authentication**: Secure HTTP endpoint access
- **Health Monitoring**: Background health checks with automatic circuit recovery
- **Zero Dependencies**: Pure Java implementation using JDK built-in HTTP server

## Tools

| Tool | Description | Requirements |
|------|-------------|--------------|
| `GetUAXDArticles` | Retrieves UAXD dashboard articles for a user | VPN |
| `GetASArticles` | Retrieves Author Services articles for a user | VPN |
| `GetRexArticles` | Retrieves Rex/Atypon platform articles for a user | None |

## Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                         NGINX (443)                              │
│          https://ailab.eastus2.cloudapp.azure.com               │
├─────────────────────────────────────────────────────────────────┤
│  /mcp/*  ─────► localhost:8478 (UAXD MCP HTTP Server)           │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                    UAXD MCP HTTP Server                          │
│                    (Java 25 + Virtual Threads)                   │
├─────────────────────────────────────────────────────────────────┤
│  Reliability Layer                                               │
│    ├── CircuitBreaker (per service)                              │
│    ├── ServiceHealthChecker                                      │
│    └── Automatic recovery                                        │
├─────────────────────────────────────────────────────────────────┤
│  Tools (with availability status)                                │
│    ├── GetRexArticles      ✓ Always available (external)        │
│    ├── GetASArticles       ⚡ VPN required (circuit breaker)     │
│    └── GetUAXDArticles     ⚡ VPN required (circuit breaker)     │
└─────────────────────────────────────────────────────────────────┘
```

## Quick Start

### Windows Installation (Claude Desktop)

**One-liner install (no admin required):**

```powershell
irm https://raw.githubusercontent.com/gmartinssi/as-uaxd-mcp/main/install.ps1 | iex
```

Or with execution policy bypass:

```powershell
powershell -ExecutionPolicy Bypass -Command "irm https://raw.githubusercontent.com/gmartinssi/as-uaxd-mcp/main/install.ps1 | iex"
```

**What it does:**
1. Downloads bundled package (~50MB) with Java 25 runtime included
2. Extracts to `%LOCALAPPDATA%\Programs\as-uaxd-mcp`
3. Configures Claude Desktop automatically
4. No admin permissions required

**After installation:**
1. Restart Claude Desktop completely (quit from system tray)
2. Look for the hammer icon (🔨) in the chat input
3. Click it to see available UAXD tools

### Linux/macOS Installation

**Prerequisites:**

- Java 25 (install via SDKMAN: `sdk install java 25-open`)
- Maven 3.9+ (install via SDKMAN: `sdk install maven`)

**Build:**

```bash
mvn clean package
```

**Run (STDIO Mode):**

```bash
./uaxd-mcp.sh
```

**Run (HTTP Mode):**

```bash
java --enable-preview -jar target/uaxd-mcp.jar --http --port=8478
```

## Configuration

### Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `MCP_API_KEY` | API key for HTTP authentication | None (auth disabled) |

### Command Line Arguments

| Argument | Description |
|----------|-------------|
| `--http` | Start in HTTP mode (default: STDIO) |
| `--port=<port>` | HTTP server port (default: 8478) |

## API Endpoints

### HTTP Mode

| Endpoint | Method | Auth | Description |
|----------|--------|------|-------------|
| `/mcp` | POST | Required | JSON-RPC requests |
| `/mcp/health` | GET | No | Health check |
| `/mcp/status` | GET | No | Service status with circuit breaker states |

### Authentication

HTTP requests require the `X-API-Key` header:

```bash
curl -X POST https://ailab.eastus2.cloudapp.azure.com/mcp \
  -H "Content-Type: application/json" \
  -H "X-API-Key: your-api-key" \
  -d '{"jsonrpc":"2.0","id":1,"method":"tools/list"}'
```

## Claude Code Integration

### STDIO Mode (Local)

Add to `~/.claude/.mcp.json`:

```json
{
  "mcpServers": {
    "uaxd": {
      "command": "/path/to/uaxd-mcp/uaxd-mcp.sh",
      "args": []
    }
  }
}
```

### HTTP Mode (Remote)

Add to `~/.claude/.mcp.json`:

```json
{
  "mcpServers": {
    "uaxd-http": {
      "type": "http",
      "url": "https://ailab.eastus2.cloudapp.azure.com/mcp",
      "headers": {
        "X-API-Key": "${UAXD_MCP_API_KEY}"
      }
    }
  }
}
```

Set the environment variable:

```bash
export UAXD_MCP_API_KEY="your-api-key"
```

## Systemd Service

### Installation

```bash
sudo cp uaxd-mcp.service /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable uaxd-mcp
sudo systemctl start uaxd-mcp
```

### Management

```bash
sudo systemctl status uaxd-mcp   # Check status
sudo systemctl restart uaxd-mcp  # Restart
sudo systemctl stop uaxd-mcp     # Stop
journalctl -u uaxd-mcp -f        # View logs
```

## Project Structure

```
uaxd-mcp/
├── pom.xml                          # Maven build configuration
├── uaxd-mcp.sh                      # STDIO launcher script
├── README.md                        # This file
├── src/main/java/com/wiley/uaxd/mcp/
│   ├── App.java                     # Entry point
│   ├── auth/                        # Authentication
│   │   ├── boundary/
│   │   │   └── TokenManager.java    # Token caching
│   │   └── control/
│   │       ├── WppAuthClient.java   # WPP authentication
│   │       └── OAuthClient.java     # OAuth2 client
│   ├── base/                        # Core protocol
│   │   ├── boundary/
│   │   │   └── CoreSTDIOProtocol.java
│   │   └── control/
│   │       └── MessageSender.java
│   ├── http/                        # HTTP client
│   │   └── control/
│   │       └── HttpClientWrapper.java
│   ├── jsonrpc/                     # JSON-RPC utilities
│   │   └── entity/
│   │       ├── JsonParser.java
│   │       └── JsonRPCResponses.java
│   ├── log/                         # Logging
│   │   └── boundary/
│   │       └── Log.java
│   ├── reliability/                 # Fault tolerance
│   │   ├── boundary/
│   │   │   └── ServiceRegistry.java
│   │   ├── control/
│   │   │   ├── CircuitBreaker.java
│   │   │   └── HealthChecker.java
│   │   └── entity/
│   │       ├── CircuitState.java
│   │       └── ServiceStatus.java
│   ├── router/                      # Request routing
│   │   ├── boundary/
│   │   │   ├── FrontDoor.java
│   │   │   └── RequestHandler.java
│   │   └── entity/
│   │       ├── Capability.java
│   │       └── MCPRequest.java
│   ├── server/                      # HTTP server
│   │   ├── boundary/
│   │   │   └── McpHttpServer.java
│   │   ├── control/
│   │   │   ├── ApiKeyAuthenticator.java
│   │   │   ├── HttpMessageSender.java
│   │   │   └── HttpRequestHandler.java
│   │   └── entity/
│   │       └── ServerConfig.java
│   └── tools/                       # MCP tools
│       ├── boundary/
│       │   └── ToolsSTDIOProtocol.java
│       ├── control/
│       │   ├── GetUAXDArticles.java
│       │   ├── GetASArticles.java
│       │   ├── GetRexArticles.java
│       │   ├── ToolInstance.java
│       │   └── ToolLocator.java
│       └── entity/
│           └── ToolSpec.java
└── src/main/resources/
    └── META-INF/services/
        └── java.util.function.Function  # SPI registration
```

## Circuit Breaker

The server implements the circuit breaker pattern to handle VPN-dependent service failures gracefully:

```
Normal Operation (CLOSED)
    │
    ▼
Failure occurs
    │
    ├── failureCount < 3 ─► Stay CLOSED, increment counter
    │
    └── failureCount >= 3 ─► Switch to OPEN
                                  │
                                  ▼
                            Wait 1 minute
                                  │
                                  ▼
                            HALF_OPEN (try one request)
                                  │
                            ├── Success ─► CLOSED (reset)
                            └── Failure ─► OPEN (restart timer)
```

### Service Status Response

```json
{
  "service": "uaxd-mcp",
  "version": "1.0.0",
  "uptime_seconds": 3600,
  "requests_processed": 150,
  "virtual_threads": true,
  "services": {
    "GetRexArticles": {
      "state": "CLOSED",
      "status": "AVAILABLE",
      "failure_count": 0
    },
    "GetUAXDArticles": {
      "state": "OPEN",
      "status": "UNAVAILABLE",
      "failure_count": 15
    }
  }
}
```

## MCP Protocol

This server implements the [Model Context Protocol](https://modelcontextprotocol.io/) specification (version 2024-11-05).

### Supported Methods

| Method | Description |
|--------|-------------|
| `initialize` | Initialize the MCP session |
| `initialized` | Client acknowledgment (notification) |
| `ping` | Health check |
| `tools/list` | List available tools |
| `tools/call` | Execute a tool |

### Example: List Tools

```bash
curl -X POST https://ailab.eastus2.cloudapp.azure.com/mcp \
  -H "Content-Type: application/json" \
  -H "X-API-Key: your-api-key" \
  -d '{"jsonrpc":"2.0","id":1,"method":"tools/list"}'
```

### Example: Call Tool

```bash
curl -X POST https://ailab.eastus2.cloudapp.azure.com/mcp \
  -H "Content-Type: application/json" \
  -H "X-API-Key: your-api-key" \
  -d '{
    "jsonrpc":"2.0",
    "id":2,
    "method":"tools/call",
    "params":{
      "name":"GetRexArticles",
      "arguments":{"userId":"user-uuid-here"}
    }
  }'
```

## License

Internal Wiley project.

## References

- [Model Context Protocol Specification](https://modelcontextprotocol.io/)
- [zmcp - Reference Implementation](https://github.com/AdamBien/zmcp)
- [Claude Code Documentation](https://docs.anthropic.com/claude-code)
