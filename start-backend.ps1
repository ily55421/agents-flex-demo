# start-backend.ps1
# Start the Spring Boot backend on port 8080.
# Usage: powershell -NoProfile -ExecutionPolicy Bypass -File .\start-backend.ps1
$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot

function Test-PortInUse([int]$Port) {
    try {
        $c = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction Stop
        return $null -ne $c
    } catch {
        $line = netstat -ano | Select-String (":$Port\s") | Select-String "LISTENING"
        return $null -ne $line
    }
}

if (!(Get-Command java -ErrorAction SilentlyContinue)) {
    Write-Host "[backend] ERROR: java not found in PATH (JDK 8+ required)" -ForegroundColor Red
    exit 1
}
if (!(Get-Command mvn -ErrorAction SilentlyContinue)) {
    Write-Host "[backend] ERROR: mvn not found in PATH (Maven 3.x required)" -ForegroundColor Red
    exit 1
}
if (Test-PortInUse 8080) {
    Write-Host "[backend] ERROR: port 8080 is already in use, backend NOT started" -ForegroundColor Red
    exit 1
}

Write-Host "[backend] Starting Spring Boot on http://localhost:8080 (mvn spring-boot:run) ..." -ForegroundColor Cyan
mvn -B spring-boot:run
