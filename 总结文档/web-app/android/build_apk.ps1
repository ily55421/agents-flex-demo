# DocBlog Android APK Build Script - No Android Studio needed
$ErrorActionPreference = "Stop"
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12

$projectRoot = "d:\阅读\总结文档\web-app\android"
$toolsDir = Join-Path $projectRoot "build-tools"
$jdkDir = Join-Path $toolsDir "jdk-17"
$androidSdkDir = Join-Path $toolsDir "android-sdk"

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  DocBlog Android APK Builder" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan

New-Item -ItemType Directory -Path $toolsDir -Force | Out-Null

# ========== Step 1: JDK 17 ==========
Write-Host "`n[1/4] Setting up JDK 17..." -ForegroundColor Green
$javaExe = Join-Path $jdkDir "bin\java.exe"
if (!(Test-Path $javaExe)) {
    Write-Host "  Downloading JDK 17 from Adoptium..." -ForegroundColor Yellow
    $jdkZip = Join-Path $toolsDir "jdk17.zip"
    $ProgressPreference = 'SilentlyContinue'
    $jdkUrl = "https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.12%2B7/OpenJDK17U-jdk_x64_windows_hotspot_17.0.12_7.zip"
    try {
        Invoke-WebRequest -Uri $jdkUrl -OutFile $jdkZip -UseBasicParsing -TimeoutSec 600
    } catch {
        Write-Host "  GitHub failed, trying Tencent mirror..." -ForegroundColor Yellow
        $jdkUrl = "https://mirrors.cloud.tencent.com/Adoptium/17/jdk/x64/windows/OpenJDK17U-jdk_x64_windows_hotspot_17.0.12_7.zip"
        Invoke-WebRequest -Uri $jdkUrl -OutFile $jdkZip -UseBasicParsing -TimeoutSec 600
    }
    Write-Host "  Extracting..." -ForegroundColor Gray
    Expand-Archive -Path $jdkZip -DestinationPath $toolsDir -Force
    $jdkExtracted = Get-ChildItem $toolsDir -Directory | Where-Object { $_.Name -like "jdk-17*" } | Select-Object -First 1
    Move-Item $jdkExtracted.FullName $jdkDir -Force
    Remove-Item $jdkZip -Force
}
& $javaExe -version
Write-Host "  JDK 17 ready" -ForegroundColor Green

# ========== Step 2: Android SDK Command-line Tools ==========
Write-Host "`n[2/4] Setting up Android SDK..." -ForegroundColor Green
$sdkmanager = Join-Path $androidSdkDir "cmdline-tools\latest\bin\sdkmanager.bat"
if (!(Test-Path $sdkmanager)) {
    Write-Host "  Downloading commandlinetools..." -ForegroundColor Yellow
    $sdkZip = Join-Path $toolsDir "cmdline-tools.zip"
    $ProgressPreference = 'SilentlyContinue'
    Invoke-WebRequest -Uri "https://dl.google.com/android/repository/commandlinetools-win-11076708_latest.zip" -OutFile $sdkZip -UseBasicParsing -TimeoutSec 600
    Write-Host "  Extracting..." -ForegroundColor Gray
    $tempDir = Join-Path $toolsDir "sdk-temp"
    if (Test-Path $tempDir) { Remove-Item $tempDir -Recurse -Force }
    Expand-Archive -Path $sdkZip -DestinationPath $tempDir -Force
    $cmdlineDir = Join-Path $androidSdkDir "cmdline-tools"
    New-Item -ItemType Directory -Path $cmdlineDir -Force | Out-Null
    if (Test-Path (Join-Path $cmdlineDir "latest")) { Remove-Item (Join-Path $cmdlineDir "latest") -Recurse -Force }
    Move-Item (Join-Path $tempDir "cmdline-tools") (Join-Path $cmdlineDir "latest") -Force
    Remove-Item $sdkZip -Force
    Remove-Item $tempDir -Recurse -Force
}

# Accept licenses
$licenseDir = Join-Path $androidSdkDir "licenses"
New-Item -ItemType Directory -Path $licenseDir -Force | Out-Null
Set-Content -Path (Join-Path $licenseDir "android-sdk-license") -Value "`nd56f5187479451eabf01fb78af6dfcb131a6481e`n24333f8a63b6825ea9c5514f83c2829b004d1fee" -Force
Set-Content -Path (Join-Path $licenseDir "android-sdk-preview-license") -Value "`n84831b9409646a918e30573bab4c9c91346d8abd" -Force
$null | Out-File -FilePath (Join-Path $androidSdkDir "repositories.cfg") -Force

# Install SDK components
$platformDir = Join-Path $androidSdkDir "platforms\android-34"
if (!(Test-Path $platformDir)) {
    Write-Host "  Installing platforms;android-34 build-tools;34.0.0 platform-tools..." -ForegroundColor Yellow
    Write-Host "  (downloads ~200MB)" -ForegroundColor Gray
    $env:JAVA_HOME = $jdkDir
    $env:PATH = "$jdkDir\bin;$env:PATH"
    $args = @("--sdk_root=$androidSdkDir", "platforms;android-34", "build-tools;34.0.0", "platform-tools")
    echo y | echo y | echo y | & $sdkmanager @args
}
Write-Host "  Android SDK ready" -ForegroundColor Green

# ========== Set environment variables ==========
$env:JAVA_HOME = $jdkDir
$env:ANDROID_HOME = $androidSdkDir
$env:ANDROID_SDK_ROOT = $androidSdkDir
$env:PATH = "$jdkDir\bin;$androidSdkDir\platform-tools;$env:PATH"

# ========== Step 3: Check assets ==========
Write-Host "`n[3/4] Checking assets..." -ForegroundColor Green
$assetsDb = Join-Path $projectRoot "app\src\main\assets\data\docs.db.zip"
if (!(Test-Path $assetsDb)) {
    Write-Host "  Running prepare_assets.py..." -ForegroundColor Yellow
    Push-Location (Split-Path $projectRoot -Parent)
    python prepare_assets.py
    Pop-Location
}
Write-Host "  Assets ready" -ForegroundColor Green

# ========== Step 4: Gradle Build ==========
Write-Host "`n[4/4] Starting Gradle build (first run downloads dependencies, 10-20 min)..." -ForegroundColor Green
Set-Location $projectRoot
& .\gradlew.bat assembleDebug --no-daemon

Write-Host "`nBuild result:" -ForegroundColor Green
$apkPath = Join-Path $projectRoot "app\build\outputs\apk\debug\app-debug.apk"
if (Test-Path $apkPath) {
    $size = [math]::Round((Get-Item $apkPath).Length / 1MB, 2)
    Write-Host "========================================" -ForegroundColor Green
    Write-Host "  BUILD SUCCESSFUL!" -ForegroundColor Green
    Write-Host "  APK: $apkPath" -ForegroundColor Cyan
    Write-Host "  Size: $size MB" -ForegroundColor Cyan
    Write-Host "========================================" -ForegroundColor Green
} else {
    Write-Host "BUILD FAILED - check errors above" -ForegroundColor Red
    exit 1
}
