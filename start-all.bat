@echo off
rem One-click launcher for Agents-Flex Showcase (double-click friendly).
rem Starts backend (18080) and frontend (15173) in separate windows.
rem Override ports by setting BACKEND_PORT / FRONTEND_PORT before running.
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0start-all.ps1"
