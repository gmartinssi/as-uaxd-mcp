#Requires -Version 5.1
<#
.SYNOPSIS
    AS-UAXD MCP Server Demo Script for Windows

.DESCRIPTION
    Tests the MCP server installation on Windows/Claude Desktop

.EXAMPLE
    .\demo.ps1
#>

$ErrorActionPreference = 'Continue'

# Configuration
$InstallDir = Join-Path $env:LOCALAPPDATA "Programs\as-uaxd-mcp"
$LauncherPath = Join-Path $InstallDir "uaxd-mcp.cmd"
$TestUserId = "00000000-0000-0000-0000-000000000001"

# Banner
Write-Host ""
Write-Host "  ╔═══════════════════════════════════════════════════════════╗" -ForegroundColor Cyan
Write-Host "  ║         AS-UAXD MCP Server Demo (Windows)                 ║" -ForegroundColor Cyan
Write-Host "  ╚═══════════════════════════════════════════════════════════╝" -ForegroundColor Cyan
Write-Host ""

# Check installation
if (-not (Test-Path $LauncherPath)) {
    Write-Host "  ❌ MCP Server not installed!" -ForegroundColor Red
    Write-Host "  Run: irm https://raw.githubusercontent.com/gmartinssi/as-uaxd-mcp/main/install.ps1 | iex" -ForegroundColor Yellow
    exit 1
}

Write-Host "  ✓ Installation found at: $InstallDir" -ForegroundColor Green
Write-Host ""

# Function to send request
function Send-McpRequest {
    param(
        [string]$Request,
        [string]$Description
    )

    Write-Host "  ═══════════════════════════════════════════════════════════" -ForegroundColor Cyan
    Write-Host "  ▶ $Description" -ForegroundColor Yellow
    Write-Host "  Request: $Request" -ForegroundColor DarkGray

    try {
        $process = New-Object System.Diagnostics.Process
        $process.StartInfo.FileName = $LauncherPath
        $process.StartInfo.UseShellExecute = $false
        $process.StartInfo.RedirectStandardInput = $true
        $process.StartInfo.RedirectStandardOutput = $true
        $process.StartInfo.RedirectStandardError = $true
        $process.StartInfo.CreateNoWindow = $true

        $process.Start() | Out-Null

        $process.StandardInput.WriteLine($Request)
        $process.StandardInput.Close()

        # Wait for response with timeout
        $response = ""
        $timeout = 30000 # 30 seconds
        $sw = [System.Diagnostics.Stopwatch]::StartNew()

        while ($sw.ElapsedMilliseconds -lt $timeout) {
            $line = $process.StandardOutput.ReadLine()
            if ($line) {
                $response = $line
                break
            }
            Start-Sleep -Milliseconds 100
        }

        $process.Kill() 2>$null

        if ($response) {
            # Try to format JSON
            try {
                $json = $response | ConvertFrom-Json
                $formatted = $json | ConvertTo-Json -Depth 10
                Write-Host "  Response:" -ForegroundColor Green
                $formatted -split "`n" | ForEach-Object { Write-Host "    $_" -ForegroundColor Green }
            }
            catch {
                Write-Host "  Response: $response" -ForegroundColor Green
            }
        }
        else {
            Write-Host "  Response: (timeout or no response)" -ForegroundColor Yellow
        }
    }
    catch {
        Write-Host "  Error: $_" -ForegroundColor Red
    }

    Write-Host ""
}

# Tests
Write-Host "  1. Initialize MCP Session" -ForegroundColor Cyan
Send-McpRequest `
    -Request '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2024-11-05","capabilities":{},"clientInfo":{"name":"demo-client","version":"1.0.0"}}}' `
    -Description "Initialize"

Write-Host "  2. List Available Tools" -ForegroundColor Cyan
Send-McpRequest `
    -Request '{"jsonrpc":"2.0","id":2,"method":"tools/list"}' `
    -Description "Tools List"

Write-Host "  3. Test GetRexArticles (No VPN Required)" -ForegroundColor Cyan
Send-McpRequest `
    -Request "{`"jsonrpc`":`"2.0`",`"id`":3,`"method`":`"tools/call`",`"params`":{`"name`":`"GetRexArticles`",`"arguments`":{`"userId`":`"$TestUserId`"}}}" `
    -Description "GetRexArticles"

Write-Host "  4. Test GetUAXDArticles (VPN Required)" -ForegroundColor Cyan
Send-McpRequest `
    -Request "{`"jsonrpc`":`"2.0`",`"id`":4,`"method`":`"tools/call`",`"params`":{`"name`":`"GetUAXDArticles`",`"arguments`":{`"userId`":`"$TestUserId`"}}}" `
    -Description "GetUAXDArticles (may fail without VPN)"

Write-Host "  5. Test GetASArticles (VPN Required)" -ForegroundColor Cyan
Send-McpRequest `
    -Request "{`"jsonrpc`":`"2.0`",`"id`":5,`"method`":`"tools/call`",`"params`":{`"name`":`"GetASArticles`",`"arguments`":{`"userId`":`"$TestUserId`"}}}" `
    -Description "GetASArticles (may fail without VPN)"

Write-Host "  6. Test Ping" -ForegroundColor Cyan
Send-McpRequest `
    -Request '{"jsonrpc":"2.0","id":6,"method":"ping"}' `
    -Description "Ping"

# Summary
Write-Host ""
Write-Host "  ╔═══════════════════════════════════════════════════════════╗" -ForegroundColor Green
Write-Host "  ║                    Demo Complete!                         ║" -ForegroundColor Green
Write-Host "  ╚═══════════════════════════════════════════════════════════╝" -ForegroundColor Green
Write-Host ""
Write-Host "  Notes:" -ForegroundColor White
Write-Host "    • GetRexArticles works without VPN" -ForegroundColor Gray
Write-Host "    • GetUAXDArticles and GetASArticles require VPN" -ForegroundColor Gray
Write-Host "    • Logs: $env:USERPROFILE\uaxd-mcp.log" -ForegroundColor Gray
Write-Host ""
