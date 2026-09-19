# start-all.ps1
# One-click launcher: checks prerequisites + ports, then starts backend (18080)
# and frontend (15173) each in its own console window.
# Ports are overridable: $env:BACKEND_PORT / $env:FRONTEND_PORT
# Usage: powershell -NoProfile -ExecutionPolicy Bypass -File .\start-all.ps1
$ErrorActionPreference = "Stop"
$Root = $PSScriptRoot

$BackendPort = if ($env:BACKEND_PORT) { [int]$env:BACKEND_PORT } else { 18080 }
$FrontendPort = if ($env:FRONTEND_PORT) { [int]$env:FRONTEND_PORT } else { 15173 }

function Test-PortInUse([int]$Port) {
    try {
        $c = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction Stop
        return $null -ne $c
    } catch {
        $line = netstat -ano | Select-String (":$Port\s") | Select-String "LISTENING"
        return $null -ne $line
    }
}

Write-Host "=== Agents-Flex Showcase Launcher ===" -ForegroundColor Green

# 1. Prerequisite check
$missing = @()
if (!(Get-Command java -ErrorAction SilentlyContinue)) { $missing += "java (JDK 8+)" }
if (!(Get-Command mvn -ErrorAction SilentlyContinue)) { $missing += "mvn (Maven 3.x)" }
if (!(Get-Command node -ErrorAction SilentlyContinue)) { $missing += "node (Node.js 18+)" }
if (!(Get-Command pnpm -ErrorAction SilentlyContinue)) { $missing += "pnpm" }
if ($missing.Count -gt 0) {
    Write-Host "[ERROR] Missing prerequisites: $($missing -join ', ')" -ForegroundColor Red
    exit 1
}

# 2. Port check (backend 18080, frontend 15173)
if (Test-PortInUse $BackendPort) {
    Write-Host "[ERROR] Port $BackendPort is already in use. Free it first, or set `$env:BACKEND_PORT to another port." -ForegroundColor Red
    exit 1
}
if (Test-PortInUse $FrontendPort) {
    Write-Host "[ERROR] Port $FrontendPort is already in use. Free it first, or set `$env:FRONTEND_PORT to another port." -ForegroundColor Red
    exit 1
}

# 3. Frontend dependencies
if (!(Test-Path "$Root\frontend\node_modules")) {
    Write-Host "[frontend] node_modules missing, running pnpm install ..." -ForegroundColor Yellow
    Push-Location "$Root\frontend"
    pnpm install
    if ($LASTEXITCODE -ne 0) { Pop-Location; exit 1 }
    Pop-Location
}

# 4. Start both services in separate windows
$backendArgs = @("-NoExit", "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", "`"$Root\start-backend.ps1`"")
Start-Process powershell -ArgumentList $backendArgs
$frontendArgs = @("-NoExit", "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", "`"$Root\start-frontend.ps1`"")
Start-Process powershell -ArgumentList $frontendArgs

Write-Host ""
Write-Host "Both services are starting in separate windows:" -ForegroundColor Green
Write-Host "  Backend  -> http://localhost:$BackendPort  (Spring Boot)" -ForegroundColor Green
Write-Host "  Frontend -> http://localhost:$FrontendPort  (Vite, /api proxied to $BackendPort)" -ForegroundColor Green
Write-Host "Open http://127.0.0.1:$FrontendPort in your browser once both windows report ready." -ForegroundColor Green
