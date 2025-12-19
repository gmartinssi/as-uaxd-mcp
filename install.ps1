#Requires -Version 5.1
<#
.SYNOPSIS
    Installs AS-UAXD MCP Server for Claude Desktop (No Admin Required)

.DESCRIPTION
    Downloads and installs the AS-UAXD MCP server with bundled Java runtime.
    Configures Claude Desktop to use the MCP server automatically.

.PARAMETER Force
    Overwrite existing installation without prompting.

.EXAMPLE
    irm https://raw.githubusercontent.com/wiley/as-uaxd-mcp/main/install.ps1 | iex

.EXAMPLE
    .\install.ps1 -Force

.NOTES
    No administrator permissions required.
    All files installed to %LOCALAPPDATA%\Programs\as-uaxd-mcp
#>

[CmdletBinding()]
param(
    [switch]$Force
)

$ErrorActionPreference = 'Stop'
$ProgressPreference = 'Continue'

# Configuration
$RepoOwner = "wiley"
$RepoName = "as-uaxd-mcp"
$InstallDir = Join-Path $env:LOCALAPPDATA "Programs\as-uaxd-mcp"
$ClaudeConfigPath = Join-Path $env:APPDATA "Claude\claude_desktop_config.json"

# Banner
function Show-Banner {
    Write-Host ""
    Write-Host "  ╔═══════════════════════════════════════════════════════════╗" -ForegroundColor Cyan
    Write-Host "  ║                                                           ║" -ForegroundColor Cyan
    Write-Host "  ║         AS-UAXD MCP Server Installer                      ║" -ForegroundColor Cyan
    Write-Host "  ║         For Claude Desktop (No Admin Required)            ║" -ForegroundColor Cyan
    Write-Host "  ║                                                           ║" -ForegroundColor Cyan
    Write-Host "  ╚═══════════════════════════════════════════════════════════╝" -ForegroundColor Cyan
    Write-Host ""
}

# Check prerequisites
function Test-Prerequisites {
    Write-Host "[1/6] Checking prerequisites..." -ForegroundColor Yellow

    # Check PowerShell version
    if ($PSVersionTable.PSVersion.Major -lt 5) {
        throw "PowerShell 5.1 or higher is required. Current version: $($PSVersionTable.PSVersion)"
    }
    Write-Host "  ✓ PowerShell $($PSVersionTable.PSVersion)" -ForegroundColor Green

    # Check internet connectivity
    try {
        $null = Invoke-WebRequest -Uri "https://github.com" -UseBasicParsing -TimeoutSec 10
        Write-Host "  ✓ Internet connectivity" -ForegroundColor Green
    }
    catch {
        throw "Cannot connect to GitHub. Please check your internet connection."
    }

    # Check disk space (need ~100MB)
    $drive = (Get-Item $env:LOCALAPPDATA).PSDrive
    $freeSpaceGB = [math]::Round((Get-PSDrive $drive.Name).Free / 1GB, 2)
    if ($freeSpaceGB -lt 0.1) {
        throw "Insufficient disk space. Need at least 100MB, have ${freeSpaceGB}GB"
    }
    Write-Host "  ✓ Disk space: ${freeSpaceGB}GB available" -ForegroundColor Green
}

# Get latest release info
function Get-LatestRelease {
    Write-Host "[2/6] Finding latest release..." -ForegroundColor Yellow

    $apiUrl = "https://api.github.com/repos/$RepoOwner/$RepoName/releases/latest"

    try {
        $release = Invoke-RestMethod -Uri $apiUrl -UseBasicParsing
        $version = $release.tag_name
        $asset = $release.assets | Where-Object { $_.name -eq "uaxd-mcp-windows-x64.zip" }

        if (-not $asset) {
            throw "Windows bundle not found in release"
        }

        Write-Host "  ✓ Found version: $version" -ForegroundColor Green

        return @{
            Version = $version
            DownloadUrl = $asset.browser_download_url
            Size = [math]::Round($asset.size / 1MB, 2)
        }
    }
    catch {
        throw "Failed to get release info: $_"
    }
}

# Download and extract bundle
function Install-Bundle {
    param($ReleaseInfo)

    Write-Host "[3/6] Downloading bundle ($($ReleaseInfo.Size)MB)..." -ForegroundColor Yellow

    # Create temp directory
    $tempDir = Join-Path $env:TEMP "uaxd-mcp-install"
    $zipPath = Join-Path $tempDir "uaxd-mcp-windows-x64.zip"

    if (Test-Path $tempDir) {
        Remove-Item $tempDir -Recurse -Force
    }
    New-Item -ItemType Directory -Path $tempDir -Force | Out-Null

    # Download with progress
    try {
        $webClient = New-Object System.Net.WebClient
        $webClient.DownloadFile($ReleaseInfo.DownloadUrl, $zipPath)
        Write-Host "  ✓ Downloaded successfully" -ForegroundColor Green
    }
    catch {
        throw "Download failed: $_"
    }

    Write-Host "[4/6] Installing to $InstallDir..." -ForegroundColor Yellow

    # Remove existing installation if present
    if (Test-Path $InstallDir) {
        if (-not $Force) {
            $response = Read-Host "  Existing installation found. Overwrite? (y/N)"
            if ($response -ne 'y' -and $response -ne 'Y') {
                throw "Installation cancelled by user"
            }
        }
        Remove-Item $InstallDir -Recurse -Force
    }

    # Create install directory
    New-Item -ItemType Directory -Path $InstallDir -Force | Out-Null

    # Extract
    try {
        Expand-Archive -Path $zipPath -DestinationPath $tempDir -Force
        $extractedDir = Join-Path $tempDir "uaxd-mcp"
        Copy-Item -Path "$extractedDir\*" -Destination $InstallDir -Recurse -Force
        Write-Host "  ✓ Installed to $InstallDir" -ForegroundColor Green
    }
    catch {
        throw "Extraction failed: $_"
    }

    # Cleanup temp
    Remove-Item $tempDir -Recurse -Force -ErrorAction SilentlyContinue

    # Verify installation
    $launcherPath = Join-Path $InstallDir "uaxd-mcp.cmd"
    if (-not (Test-Path $launcherPath)) {
        throw "Installation verification failed: launcher not found"
    }

    return $launcherPath
}

# Configure Claude Desktop
function Set-ClaudeDesktopConfig {
    param($LauncherPath)

    Write-Host "[5/6] Configuring Claude Desktop..." -ForegroundColor Yellow

    $claudeDir = Split-Path $ClaudeConfigPath -Parent

    # Check if Claude Desktop is installed
    if (-not (Test-Path $claudeDir)) {
        Write-Host "  ⚠ Claude Desktop config directory not found" -ForegroundColor Yellow
        Write-Host "  ⚠ You may need to configure manually after installing Claude Desktop" -ForegroundColor Yellow
        return
    }

    # Backup existing config
    if (Test-Path $ClaudeConfigPath) {
        $backupPath = "$ClaudeConfigPath.backup"
        Copy-Item $ClaudeConfigPath $backupPath -Force
        Write-Host "  ✓ Backed up existing config to $backupPath" -ForegroundColor Green
    }

    # Read or create config
    $config = @{ mcpServers = @{} }
    if (Test-Path $ClaudeConfigPath) {
        try {
            $existingConfig = Get-Content $ClaudeConfigPath -Raw | ConvertFrom-Json -AsHashtable
            if ($existingConfig.mcpServers) {
                $config.mcpServers = $existingConfig.mcpServers
            }
        }
        catch {
            Write-Host "  ⚠ Could not parse existing config, creating new one" -ForegroundColor Yellow
        }
    }

    # Add UAXD MCP server - escape backslashes for JSON
    $escapedPath = $LauncherPath.Replace('\', '\\')
    $config.mcpServers["uaxd"] = @{
        command = $LauncherPath
        args = @()
    }

    # Write config
    $configJson = $config | ConvertTo-Json -Depth 10
    Set-Content -Path $ClaudeConfigPath -Value $configJson -Encoding UTF8

    Write-Host "  ✓ Claude Desktop configured" -ForegroundColor Green
}

# Show success message
function Show-Success {
    param($LauncherPath)

    Write-Host "[6/6] Installation complete!" -ForegroundColor Yellow
    Write-Host ""
    Write-Host "  ╔═══════════════════════════════════════════════════════════╗" -ForegroundColor Green
    Write-Host "  ║                    Installation Complete!                 ║" -ForegroundColor Green
    Write-Host "  ╚═══════════════════════════════════════════════════════════╝" -ForegroundColor Green
    Write-Host ""
    Write-Host "  Installed to: $InstallDir" -ForegroundColor White
    Write-Host "  Launcher:     $LauncherPath" -ForegroundColor White
    Write-Host ""
    Write-Host "  Available Tools:" -ForegroundColor Cyan
    Write-Host "    • GetUAXDArticles  (requires VPN)" -ForegroundColor White
    Write-Host "    • GetASArticles    (requires VPN)" -ForegroundColor White
    Write-Host "    • GetRexArticles   (no VPN needed)" -ForegroundColor White
    Write-Host ""
    Write-Host "  Next Steps:" -ForegroundColor Yellow
    Write-Host "    1. Restart Claude Desktop completely (quit from system tray)" -ForegroundColor White
    Write-Host "    2. Look for the hammer icon (🔨) in the chat input" -ForegroundColor White
    Write-Host "    3. Click it to see available UAXD tools" -ForegroundColor White
    Write-Host ""
    Write-Host "  Note: Some tools require VPN connection to internal Wiley services" -ForegroundColor DarkGray
    Write-Host ""
}

# Main
function Main {
    Show-Banner

    try {
        Test-Prerequisites
        $release = Get-LatestRelease
        $launcherPath = Install-Bundle -ReleaseInfo $release
        Set-ClaudeDesktopConfig -LauncherPath $launcherPath
        Show-Success -LauncherPath $launcherPath
    }
    catch {
        Write-Host ""
        Write-Host "  ❌ Installation failed: $_" -ForegroundColor Red
        Write-Host ""
        exit 1
    }
}

Main
