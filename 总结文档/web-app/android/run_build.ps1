$ErrorActionPreference = "Continue"
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

$projectDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$jdk = Join-Path $projectDir "build-tools\jdk-17"
$androidSdk = Join-Path $projectDir "build-tools\android-sdk"
$gradleHome = Join-Path $projectDir "build-tools\gradle-home"
$python311 = Join-Path $projectDir "build-tools\python-3.11"

Set-Location $projectDir

$env:JAVA_HOME = $jdk
$env:ANDROID_HOME = $androidSdk
$env:ANDROID_SDK_ROOT = $androidSdk
$env:GRADLE_USER_HOME = $gradleHome

$nullEnv = @('PYTHONHOME','PYTHONPATH','PYTHONEXECUTABLE','PYTHONSTARTUP',
    'CONDA_PREFIX','CONDA_DEFAULT_ENV','VIRTUAL_ENV')
foreach ($v in $nullEnv) { Set-Item -Path "Env:$v" -Value $null -ErrorAction SilentlyContinue }

$sys32 = Join-Path $env:SystemRoot "system32"
$wbem = Join-Path $sys32 "Wbem"
$env:PATH = "$python311;$python311\Scripts;$jdk\bin;$androidSdk\platform-tools;$sys32;$env:SystemRoot;$wbem"

Write-Host "=== Build Environment ===" -ForegroundColor Cyan
Write-Host "Project: $projectDir"
Write-Host "JAVA_HOME: $env:JAVA_HOME"
Write-Host "Python version:"
& (Join-Path $python311 "python.exe") --version
Write-Host ""

Write-Host "Stopping Gradle daemons..." -ForegroundColor Yellow
$gradlew = Join-Path $projectDir "gradlew.bat"
& $gradlew --stop 2>&1 | Out-Null
Start-Sleep -Seconds 2

Write-Host "Cleaning build directory..." -ForegroundColor Yellow
$buildDir = Join-Path $projectDir "app\build"
if (Test-Path $buildDir) { Remove-Item -Path $buildDir -Recurse -Force -ErrorAction SilentlyContinue }

Write-Host ""
Write-Host "=== Starting AssembleDebug ===" -ForegroundColor Green
Write-Host ""

& $gradlew assembleDebug --no-daemon --console=plain 2>&1

$buildResult = $LASTEXITCODE
$apkPath = Join-Path $projectDir "app\build\outputs\apk\debug\app-debug.apk"

Write-Host ""
if ($buildResult -eq 0 -and (Test-Path $apkPath)) {
    $size = [math]::Round((Get-Item $apkPath).Length / 1MB, 2)
    Write-Host "========================================" -ForegroundColor Green
    Write-Host "  BUILD SUCCESSFUL!" -ForegroundColor Green
    Write-Host "========================================" -ForegroundColor Green
    Write-Host "APK: $apkPath" -ForegroundColor Cyan
    Write-Host "Size: $size MB" -ForegroundColor Cyan
} else {
    Write-Host "========================================" -ForegroundColor Red
    Write-Host "  BUILD FAILED (exit code: $buildResult)" -ForegroundColor Red
    if (Test-Path $apkPath) { Write-Host "APK exists at: $apkPath" -ForegroundColor Yellow }
    Write-Host "========================================" -ForegroundColor Red
}
