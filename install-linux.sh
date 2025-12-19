#!/bin/bash
#
# AS-UAXD MCP Server Installer for Claude Code (Linux)
#
# Usage:
#   curl -fsSL https://raw.githubusercontent.com/gmartinssi/as-uaxd-mcp/main/install-linux.sh | bash
#
# Or with custom install directory:
#   curl -fsSL https://raw.githubusercontent.com/gmartinssi/as-uaxd-mcp/main/install-linux.sh | bash -s -- --dir ~/my-mcp
#

set -e

# Configuration
REPO_URL="https://github.com/gmartinssi/as-uaxd-mcp.git"
DEFAULT_INSTALL_DIR="$HOME/.local/share/as-uaxd-mcp"
CLAUDE_CONFIG="$HOME/.claude/.mcp.json"
REQUIRED_JAVA_VERSION=25

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m'

# Parse arguments
INSTALL_DIR="$DEFAULT_INSTALL_DIR"
while [[ $# -gt 0 ]]; do
    case $1 in
        --dir)
            INSTALL_DIR="$2"
            shift 2
            ;;
        *)
            shift
            ;;
    esac
done

# Banner
echo -e "${CYAN}"
echo "╔═══════════════════════════════════════════════════════════╗"
echo "║     AS-UAXD MCP Server Installer for Claude Code          ║"
echo "║                    (Linux)                                ║"
echo "╚═══════════════════════════════════════════════════════════╝"
echo -e "${NC}"

# Step 1: Check prerequisites
echo -e "${YELLOW}[1/5] Checking prerequisites...${NC}"

# Check git
if ! command -v git &> /dev/null; then
    echo -e "${RED}  ✗ git not found. Please install git.${NC}"
    exit 1
fi
echo -e "${GREEN}  ✓ git$(NC)"

# Check Java
JAVA_OK=false
if command -v java &> /dev/null; then
    JAVA_VERSION=$(java -version 2>&1 | head -1 | cut -d'"' -f2 | cut -d'.' -f1)
    if [[ "$JAVA_VERSION" -ge "$REQUIRED_JAVA_VERSION" ]]; then
        echo -e "${GREEN}  ✓ Java $JAVA_VERSION${NC}"
        JAVA_OK=true
    else
        echo -e "${YELLOW}  ⚠ Java $JAVA_VERSION found, but Java $REQUIRED_JAVA_VERSION+ required${NC}"
    fi
else
    echo -e "${YELLOW}  ⚠ Java not found${NC}"
fi

# Try SDKMAN if Java not OK
if [[ "$JAVA_OK" == "false" ]]; then
    if [[ -f "$HOME/.sdkman/bin/sdkman-init.sh" ]]; then
        echo -e "${YELLOW}  → Sourcing SDKMAN...${NC}"
        source "$HOME/.sdkman/bin/sdkman-init.sh"

        if command -v java &> /dev/null; then
            JAVA_VERSION=$(java -version 2>&1 | head -1 | cut -d'"' -f2 | cut -d'.' -f1)
            if [[ "$JAVA_VERSION" -ge "$REQUIRED_JAVA_VERSION" ]]; then
                echo -e "${GREEN}  ✓ Java $JAVA_VERSION (via SDKMAN)${NC}"
                JAVA_OK=true
            fi
        fi
    fi
fi

# Install Java via SDKMAN if needed
if [[ "$JAVA_OK" == "false" ]]; then
    echo -e "${YELLOW}  → Installing Java $REQUIRED_JAVA_VERSION via SDKMAN...${NC}"

    # Install SDKMAN if not present
    if [[ ! -f "$HOME/.sdkman/bin/sdkman-init.sh" ]]; then
        echo -e "${YELLOW}  → Installing SDKMAN...${NC}"
        curl -s "https://get.sdkman.io" | bash
        source "$HOME/.sdkman/bin/sdkman-init.sh"
    fi

    # Install Java 25
    source "$HOME/.sdkman/bin/sdkman-init.sh"
    sdk install java ${REQUIRED_JAVA_VERSION}-open <<< "Y" || true
    sdk use java ${REQUIRED_JAVA_VERSION}-open

    # Verify
    if command -v java &> /dev/null; then
        JAVA_VERSION=$(java -version 2>&1 | head -1 | cut -d'"' -f2 | cut -d'.' -f1)
        if [[ "$JAVA_VERSION" -ge "$REQUIRED_JAVA_VERSION" ]]; then
            echo -e "${GREEN}  ✓ Java $JAVA_VERSION installed${NC}"
            JAVA_OK=true
        fi
    fi
fi

if [[ "$JAVA_OK" == "false" ]]; then
    echo -e "${RED}  ✗ Could not install Java $REQUIRED_JAVA_VERSION${NC}"
    echo -e "${RED}  Please install manually: sdk install java ${REQUIRED_JAVA_VERSION}-open${NC}"
    exit 1
fi

# Step 2: Clone/update repository
echo -e "${YELLOW}[2/5] Installing to $INSTALL_DIR...${NC}"

if [[ -d "$INSTALL_DIR" ]]; then
    echo -e "${YELLOW}  → Updating existing installation...${NC}"
    cd "$INSTALL_DIR"
    git fetch origin
    git reset --hard origin/main
else
    echo -e "${YELLOW}  → Cloning repository...${NC}"
    git clone --depth 1 "$REPO_URL" "$INSTALL_DIR"
    cd "$INSTALL_DIR"
fi
echo -e "${GREEN}  ✓ Source code ready${NC}"

# Step 3: Build
echo -e "${YELLOW}[3/5] Building...${NC}"

# Source SDKMAN to ensure Java is available
if [[ -f "$HOME/.sdkman/bin/sdkman-init.sh" ]]; then
    source "$HOME/.sdkman/bin/sdkman-init.sh"
fi

chmod +x mvnw
./mvnw clean package -q -DskipTests
echo -e "${GREEN}  ✓ Build complete${NC}"

# Step 4: Configure Claude Code
echo -e "${YELLOW}[4/5] Configuring Claude Code...${NC}"

LAUNCHER_PATH="$INSTALL_DIR/uaxd-mcp.sh"
chmod +x "$LAUNCHER_PATH"

# Create Claude config directory if needed
mkdir -p "$(dirname "$CLAUDE_CONFIG")"

# Backup existing config
if [[ -f "$CLAUDE_CONFIG" ]]; then
    cp "$CLAUDE_CONFIG" "${CLAUDE_CONFIG}.backup"
    echo -e "${GREEN}  ✓ Backed up existing config${NC}"
fi

# Create or update config
if [[ -f "$CLAUDE_CONFIG" ]] && [[ -s "$CLAUDE_CONFIG" ]]; then
    # Update existing config using jq if available, otherwise Python
    if command -v jq &> /dev/null; then
        TEMP_CONFIG=$(mktemp)
        jq --arg cmd "$LAUNCHER_PATH" '.mcpServers.uaxd = {"command": $cmd, "args": []}' "$CLAUDE_CONFIG" > "$TEMP_CONFIG"
        mv "$TEMP_CONFIG" "$CLAUDE_CONFIG"
    elif command -v python3 &> /dev/null; then
        python3 << EOF
import json
import sys

config_path = "$CLAUDE_CONFIG"
launcher_path = "$LAUNCHER_PATH"

try:
    with open(config_path, 'r') as f:
        config = json.load(f)
except:
    config = {"mcpServers": {}}

if "mcpServers" not in config:
    config["mcpServers"] = {}

config["mcpServers"]["uaxd"] = {
    "command": launcher_path,
    "args": []
}

with open(config_path, 'w') as f:
    json.dump(config, f, indent=2)
EOF
    else
        echo -e "${YELLOW}  ⚠ Neither jq nor python3 found, creating fresh config${NC}"
        cat > "$CLAUDE_CONFIG" << EOF
{
  "mcpServers": {
    "uaxd": {
      "command": "$LAUNCHER_PATH",
      "args": []
    }
  }
}
EOF
    fi
else
    # Create fresh config
    cat > "$CLAUDE_CONFIG" << EOF
{
  "mcpServers": {
    "uaxd": {
      "command": "$LAUNCHER_PATH",
      "args": []
    }
  }
}
EOF
fi

echo -e "${GREEN}  ✓ Claude Code configured${NC}"

# Step 5: Verify
echo -e "${YELLOW}[5/5] Verifying installation...${NC}"

# Test the server
TEST_RESULT=$("$LAUNCHER_PATH" << 'EOF' 2>/dev/null | head -1
{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2024-11-05","capabilities":{},"clientInfo":{"name":"installer","version":"1.0"}}}
EOF
)

if echo "$TEST_RESULT" | grep -q '"result"'; then
    echo -e "${GREEN}  ✓ MCP server responds correctly${NC}"
else
    echo -e "${YELLOW}  ⚠ Could not verify server response${NC}"
fi

# Success
echo ""
echo -e "${GREEN}╔═══════════════════════════════════════════════════════════╗${NC}"
echo -e "${GREEN}║              Installation Complete!                       ║${NC}"
echo -e "${GREEN}╚═══════════════════════════════════════════════════════════╝${NC}"
echo ""
echo -e "  Installed to: ${CYAN}$INSTALL_DIR${NC}"
echo -e "  Config:       ${CYAN}$CLAUDE_CONFIG${NC}"
echo ""
echo -e "  ${YELLOW}Available Tools:${NC}"
echo "    • GetUAXDArticles  (requires VPN)"
echo "    • GetASArticles    (requires VPN)"
echo "    • GetRexArticles   (no VPN needed)"
echo ""
echo -e "  ${YELLOW}Test with:${NC}"
echo "    $INSTALL_DIR/demo.sh"
echo ""
echo -e "  ${YELLOW}Or in Claude Code:${NC}"
echo '    "Use GetRexArticles for user 00000000-0000-0000-0000-000000000001"'
echo ""

# Add SDKMAN init to shell rc if needed
if [[ -f "$HOME/.sdkman/bin/sdkman-init.sh" ]]; then
    SHELL_RC=""
    if [[ -f "$HOME/.bashrc" ]]; then
        SHELL_RC="$HOME/.bashrc"
    elif [[ -f "$HOME/.zshrc" ]]; then
        SHELL_RC="$HOME/.zshrc"
    fi

    if [[ -n "$SHELL_RC" ]] && ! grep -q "sdkman-init.sh" "$SHELL_RC" 2>/dev/null; then
        echo -e "  ${YELLOW}Note:${NC} Add SDKMAN to your shell by running:"
        echo "    echo 'source \"\$HOME/.sdkman/bin/sdkman-init.sh\"' >> $SHELL_RC"
        echo ""
    fi
fi
