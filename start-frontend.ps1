# start-frontend.ps1
# Start the Vite dev server on port 5173 (proxies /api to http://localhost:8080).
# Usage: powershell -NoProfile -ExecutionPolicy Bypass -File .\start-frontend.ps1
$ErrorActionPreference = "Stop"
Set-Location "$PSScriptRoot\frontend"

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
if (Test-PortInUse 5173) {
    Write-Host "[frontend] ERROR: port 5173 is already in use, frontend NOT started" -ForegroundColor Red
    exit 1
}
if (!(Test-Path "node_modules")) {
    Write-Host "[frontend] node_modules missing, running pnpm install ..." -ForegroundColor Yellow
    pnpm install
    if ($LASTEXITCODE -ne 0) { exit 1 }
}

Write-Host "[frontend] Starting Vite dev server on http://localhost:5173 (pnpm dev) ..." -ForegroundColor Cyan
pnpm dev
