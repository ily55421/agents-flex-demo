# 文档博客服务器启动脚本（带自动重启）
$port = 8080
$scriptPath = Join-Path $PSScriptRoot "server.py"

function Test-Server {
    try {
        $response = Invoke-WebRequest -Uri "http://localhost:$port/api/health" -UseBasicParsing -TimeoutSec 3
        return $response.StatusCode -eq 200
    } catch {
        return $false
    }
}

function Stop-Server {
    Get-Process -Name python -ErrorAction SilentlyContinue | Where-Object {
        $_.CommandLine -like "*$scriptPath*"
    } | Stop-Process -Force
    Start-Sleep -Seconds 2
}

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  文档博客服务器启动器" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan

# while ($true) {
#     if (-not (Test-Server)) {
#         Write-Host "`n[$(Get-Date -Format 'HH:mm:ss')] 服务器未响应，正在重启..." -ForegroundColor Yellow
#         Stop-Server
#         Start-Process -FilePath "python" -ArgumentList $scriptPath -NoNewWindow
#         Write-Host "[$(Get-Date -Format 'HH:mm:ss')] 服务器已启动" -ForegroundColor Green
#     }

#     # 每 10 秒检查一次
#     # Start-Sleep -Seconds 10
# }
