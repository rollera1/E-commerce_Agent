@echo off
setlocal

powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0start-all-services.ps1" %*
exit /b %errorlevel%
