@echo off
chcp 65001 >nul 2>&1
title 文档博客 Web 服务
echo ============================================================
echo   📚 文档博客 Web 服务
echo ============================================================
echo.

set PYTHON=%LOCALAPPDATA%\Programs\Python\Python314\python.exe
if not exist "%PYTHON%" set PYTHON=python

cd /d "%~dp0"

echo   正在启动服务...
echo   地址: http://localhost:8080
echo   按 Ctrl+C 停止
echo.

%PYTHON% -u server.py

echo.
echo   服务已停止
pause
