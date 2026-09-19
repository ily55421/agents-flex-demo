# start-frontend.ps1
# Start the Vite dev server (default port 15173, override with FRONTEND_PORT).
# Proxies /api to the backend (default 18080, override with BACKEND_PORT).
# Usage: powershell -NoProfile -ExecutionPolicy Bypass -File .\start-frontend.ps1
$ErrorActionPreference = "Stop"
Set-Location "$PSScriptRoot\frontend"

$FrontendPort = if ($env:FRONTEND_PORT) { [int]$env:FRONTEND_PORT } else { 15173 }
$BackendPort = if ($env:BACKEND_PORT) { [int]$env:BACKEND_PORT } else { 18080 }

function Test-PortInUse([int]$Port) {
    try {
        $c = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction Stop
        return $null -ne $c
    } catch {
        $line = netstat -ano | Select-String (":$Port\s") | Select-String "LISTENING"
        return $null -ne $line
    }
}

if (!(Get-Command node -ErrorAction SilentlyContinue)) {
    Write-Host "[frontend] ERROR: node not found in PATH (Node.js 18+ required)" -ForegroundColor Red
    exit 1
}
if (!(Get-Command pnpm -ErrorAction SilentlyContinue)) {
    Write-Host "[frontend] ERROR: pnpm not found in PATH (pnpm required)" -ForegroundColor Red
    exit 1
}
if (Test-PortInUse $FrontendPort) {
    Write-Host "[frontend] ERROR: port $FrontendPort is already in use, frontend NOT started" -ForegroundColor Red
    Write-Host "[frontend] Hint: free the port, or run with `$env:FRONTEND_PORT=<free port>" -ForegroundColor Yellow
    exit 1
}
if (!(Test-Path "node_modules")) {
    Write-Host "[frontend] node_modules missing, running pnpm install ..." -ForegroundColor Yellow
    pnpm install
    if ($LASTEXITCODE -ne 0) { exit 1 }
}

# vite.config.ts 读取这两个变量决定监听端口与 /api 代理目标
$env:FRONTEND_PORT = "$FrontendPort"
$env:BACKEND_PORT = "$BackendPort"

Write-Host "[frontend] Starting Vite dev server on http://localhost:$FrontendPort (pnpm dev, /api -> http://localhost:$BackendPort) ..." -ForegroundColor Cyan
pnpm dev
