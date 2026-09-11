# start-all.ps1
# One-click launcher: checks prerequisites + ports, then starts backend (8080)
# and frontend (5173) each in its own console window.
# Usage: powershell -NoProfile -ExecutionPolicy Bypass -File .\start-all.ps1
$ErrorActionPreference = "Stop"
$Root = $PSScriptRoot

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

# 2. Port check (backend 8080, frontend 5173)
if (Test-PortInUse 8080) {
    Write-Host "[ERROR] Port 8080 is already in use. Free it first or change server.port in application.yml." -ForegroundColor Red
    exit 1
}
if (Test-PortInUse 5173) {
    Write-Host "[ERROR] Port 5173 is already in use. Free it first or change server.port in vite.config.ts." -ForegroundColor Red
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

Write-Host "" -ForegroundColor Green
Write-Host "Both services are starting in separate windows:" -ForegroundColor Green
Write-Host "  Backend  -> http://localhost:8080  (Spring Boot)" -ForegroundColor Green
Write-Host "  Frontend -> http://localhost:5173  (Vite, /api proxied to 8080)" -ForegroundColor Green
Write-Host "Open http://127.0.0.1:5173 in your browser once both windows report ready." -ForegroundColor Green
