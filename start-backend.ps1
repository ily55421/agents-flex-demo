# start-backend.ps1
# Start the Spring Boot backend (default port 18080, override with BACKEND_PORT).
# Usage: powershell -NoProfile -ExecutionPolicy Bypass -File .\start-backend.ps1
$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot

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

# 解析 java 主版本号（"21.0.10" -> 21；"1.8.0_152" -> 8）
function Get-JavaMajor([string]$JavaExe) {
    try {
        $out = & $JavaExe -version 2>&1 | Out-String
        if ($out -match 'version "(\d+)\.(\d+)') {
            $major = [int]$Matches[1]
            if ($major -eq 1) { return [int]$Matches[2] }
            return $major
        }
    } catch { }
    return 0
}

# 本项目用 <java.version>21</java.version> 编译，JDK 8/17 会报「不支持发行版本 21」。
# 解析顺序：JAVA_HOME（≥21）→ PATH 中的 java（≥21）→ 常见安装目录扫描。
function Resolve-Jdk21 {
    if ($env:JAVA_HOME) {
        $cand = Join-Path $env:JAVA_HOME "bin\java.exe"
        if ((Test-Path $cand) -and ((Get-JavaMajor $cand) -ge 21)) { return $cand }
    }
    $onPath = (Get-Command java -ErrorAction SilentlyContinue).Source
    if ($onPath -and ((Get-JavaMajor $onPath) -ge 21)) { return $onPath }
    $bases = @("D:\dev\Java", "C:\Program Files\Java", "C:\Program Files\Eclipse Adoptium", "D:\Java", "$env:LOCALAPPDATA\Programs\Eclipse Adoptium")
    foreach ($base in $bases) {
        if (!(Test-Path $base)) { continue }
        foreach ($dir in (Get-ChildItem $base -Directory -ErrorAction SilentlyContinue)) {
            # 兼容解压后多一层目录的情况（jdk-21.0.10_windows-x64_bin\jdk-21.0.10\bin\java.exe）
            foreach ($sub in @($dir.FullName) + (Get-ChildItem $dir.FullName -Directory -ErrorAction SilentlyContinue | ForEach-Object { $_.FullName })) {
                $cand = Join-Path $sub "bin\java.exe"
                if ((Test-Path $cand) -and ((Get-JavaMajor $cand) -ge 21)) { return $cand }
            }
        }
    }
    return $null
}

if (!(Get-Command mvn -ErrorAction SilentlyContinue)) {
    Write-Host "[backend] ERROR: mvn not found in PATH (Maven 3.x required)" -ForegroundColor Red
    exit 1
}

$javaExe = Resolve-Jdk21
if (!$javaExe) {
    Write-Host "[backend] ERROR: no JDK 21+ found. This project compiles with <java.version>21</java.version>." -ForegroundColor Red
    Write-Host "[backend] Set JAVA_HOME to a JDK 21+ install, or install one (e.g. Temurin 21)." -ForegroundColor Yellow
    exit 1
}
$jdkHome = Split-Path (Split-Path $javaExe -Parent) -Parent
$env:JAVA_HOME = $jdkHome
$env:PATH = "$jdkHome\bin;$env:PATH"
Write-Host "[backend] Using JDK $(Get-JavaMajor $javaExe) at $jdkHome" -ForegroundColor DarkGray

if (Test-PortInUse $BackendPort) {
    Write-Host "[backend] ERROR: port $BackendPort is already in use, backend NOT started" -ForegroundColor Red
    Write-Host "[backend] Hint: free the port, or run with `$env:BACKEND_PORT=<free port>" -ForegroundColor Yellow
    exit 1
}

# application.yml 通过 ${SERVER_PORT:18080} 读取该变量
$env:SERVER_PORT = "$BackendPort"

Write-Host "[backend] Starting Spring Boot on http://localhost:$BackendPort (mvn spring-boot:run) ..." -ForegroundColor Cyan
mvn -B spring-boot:run
