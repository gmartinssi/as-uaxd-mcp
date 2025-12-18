#!/bin/bash

# UAXD MCP Server Launcher
# Usage: ./uaxd-mcp.sh

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
JAR_PATH="${SCRIPT_DIR}/target/uaxd-mcp.jar"

# Source SDKMAN to ensure Java 25 is available
if [ -f "$HOME/.sdkman/bin/sdkman-init.sh" ]; then
    source "$HOME/.sdkman/bin/sdkman-init.sh"
fi

# Verify Java is available
if ! command -v java &> /dev/null; then
    echo "Java not found. Please install Java 25." >&2
    exit 1
fi

# Run the MCP server
exec java --enable-preview -jar "${JAR_PATH}"
