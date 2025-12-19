#!/bin/bash
# AS-UAXD MCP Server Demo Script
# Tests the MCP server STDIO protocol

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
JAR_PATH="${SCRIPT_DIR}/target/uaxd-mcp.jar"

# Colors
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

echo -e "${CYAN}"
echo "╔═══════════════════════════════════════════════════════════╗"
echo "║         AS-UAXD MCP Server Demo                           ║"
echo "╚═══════════════════════════════════════════════════════════╝"
echo -e "${NC}"

# Check JAR exists
if [ ! -f "$JAR_PATH" ]; then
    echo -e "${YELLOW}Building JAR...${NC}"
    mvn -f "$SCRIPT_DIR/pom.xml" clean package -q
fi

# Function to send MCP request and get response
send_request() {
    local request="$1"
    local description="$2"

    echo -e "${YELLOW}▶ $description${NC}"
    echo -e "  Request: ${CYAN}$request${NC}"

    response=$(echo "$request" | java --enable-preview -jar "$JAR_PATH" 2>/dev/null | head -1)

    # Pretty print JSON if jq is available
    if command -v jq &> /dev/null; then
        echo -e "  Response: ${GREEN}"
        echo "$response" | jq .
        echo -e "${NC}"
    else
        echo -e "  Response: ${GREEN}$response${NC}"
        echo ""
    fi
}

echo -e "${CYAN}═══════════════════════════════════════════════════════════${NC}"
echo -e "${CYAN}1. Initialize MCP Session${NC}"
echo -e "${CYAN}═══════════════════════════════════════════════════════════${NC}"

send_request \
    '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2024-11-05","capabilities":{},"clientInfo":{"name":"demo-client","version":"1.0.0"}}}' \
    "Initialize"

echo -e "${CYAN}═══════════════════════════════════════════════════════════${NC}"
echo -e "${CYAN}2. List Available Tools${NC}"
echo -e "${CYAN}═══════════════════════════════════════════════════════════${NC}"

send_request \
    '{"jsonrpc":"2.0","id":2,"method":"tools/list"}' \
    "Tools List"

echo -e "${CYAN}═══════════════════════════════════════════════════════════${NC}"
echo -e "${CYAN}3. Test GetRexArticles (No VPN Required)${NC}"
echo -e "${CYAN}═══════════════════════════════════════════════════════════${NC}"

# Use a test UUID
TEST_USER_ID="00000000-0000-0000-0000-000000000001"

send_request \
    "{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"tools/call\",\"params\":{\"name\":\"GetRexArticles\",\"arguments\":{\"userId\":\"$TEST_USER_ID\"}}}" \
    "GetRexArticles (test user)"

echo -e "${CYAN}═══════════════════════════════════════════════════════════${NC}"
echo -e "${CYAN}4. Test GetUAXDArticles (VPN Required)${NC}"
echo -e "${CYAN}═══════════════════════════════════════════════════════════${NC}"

send_request \
    "{\"jsonrpc\":\"2.0\",\"id\":4,\"method\":\"tools/call\",\"params\":{\"name\":\"GetUAXDArticles\",\"arguments\":{\"userId\":\"$TEST_USER_ID\"}}}" \
    "GetUAXDArticles (test user - may fail without VPN)"

echo -e "${CYAN}═══════════════════════════════════════════════════════════${NC}"
echo -e "${CYAN}5. Test GetASArticles (VPN Required)${NC}"
echo -e "${CYAN}═══════════════════════════════════════════════════════════${NC}"

send_request \
    "{\"jsonrpc\":\"2.0\",\"id\":5,\"method\":\"tools/call\",\"params\":{\"name\":\"GetASArticles\",\"arguments\":{\"userId\":\"$TEST_USER_ID\"}}}" \
    "GetASArticles (test user - may fail without VPN)"

echo -e "${CYAN}═══════════════════════════════════════════════════════════${NC}"
echo -e "${CYAN}6. Test Ping${NC}"
echo -e "${CYAN}═══════════════════════════════════════════════════════════${NC}"

send_request \
    '{"jsonrpc":"2.0","id":6,"method":"ping"}' \
    "Ping"

echo -e "${CYAN}"
echo "╔═══════════════════════════════════════════════════════════╗"
echo "║                    Demo Complete!                         ║"
echo "╚═══════════════════════════════════════════════════════════╝"
echo -e "${NC}"

echo "Notes:"
echo "  - GetRexArticles works without VPN"
echo "  - GetUAXDArticles and GetASArticles require VPN connection"
echo "  - Logs are written to: ~/uaxd-mcp.log"
