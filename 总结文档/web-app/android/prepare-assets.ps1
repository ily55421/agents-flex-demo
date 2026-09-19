# Prepare assets for Android build
$ErrorActionPreference = "Stop"

$webAppDir = Split-Path -Parent $PSScriptRoot
$assetsDir = Join-Path $PSScriptRoot "app\src\main\assets"

Write-Host "Cleaning old assets..."
if (Test-Path $assetsDir) {
    Remove-Item $assetsDir -Recurse -Force
}
New-Item -ItemType Directory -Path $assetsDir -Force | Out-Null

# Copy static files
Write-Host "Copying static files..."
$staticSrc = Join-Path $webAppDir "static"
$staticDst = Join-Path $assetsDir "static"
Copy-Item -Path $staticSrc -Destination $staticDst -Recurse -Force

# Copy and zip database
Write-Host "Compressing database..."
$dataSrc = Join-Path $webAppDir "data"
$dataDst = Join-Path $assetsDir "data"
New-Item -ItemType Directory -Path $dataDst -Force | Out-Null

$dbFile = Join-Path $dataSrc "docs.db"
if (-not (Test-Path $dbFile)) {
    Write-Error "docs.db not found! Please run python server.py first."
    exit 1
}

Add-Type -AssemblyName System.IO.Compression.FileSystem
$dbZip = Join-Path $dataDst "docs.db.zip"
if (Test-Path $dbZip) { Remove-Item $dbZip -Force }

$dbSizeBefore = (Get-Item $dbFile).Length
[System.IO.Compression.ZipFile]::CreateFromEntry($dbFile, $dbZip)

$dbSizeAfter = (Get-Item $dbZip).Length
$staticCount = (Get-ChildItem -Path $staticDst -Recurse -File).Count

Write-Host ""
Write-Host "Assets prepared successfully!" -ForegroundColor Green
Write-Host "  Static files: $staticCount"
Write-Host "  DB size: $([math]::Round($dbSizeBefore/1MB, 2)) MB"
Write-Host "  Compressed: $([math]::Round($dbSizeAfter/1MB, 2)) MB"
Write-Host ""
Write-Host "Now build APK with Android Studio" -ForegroundColor Cyan
