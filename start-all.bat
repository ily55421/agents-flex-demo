@echo off
rem One-click launcher for Agents-Flex Showcase (double-click friendly).
rem Starts backend (8080) and frontend (5173) in separate windows.
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0start-all.ps1"
