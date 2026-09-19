@echo off
setlocal

for %%I in ("%~dp0..") do set "PROJECT_DIR=%%~fI"
for %%I in ("%PROJECT_DIR%\..") do set "WORKSPACE_DIR=%%~fI"
set "UV_CACHE_DIR=%WORKSPACE_DIR%\.uv-cache"
if exist "%PROJECT_DIR%\.env" (
  for /f "usebackq eol=# tokens=1,* delims==" %%A in ("%PROJECT_DIR%\.env") do (
    if not "%%A"=="" set "%%A=%%B"
  )
)
cd /d "%PROJECT_DIR%\ai-python"

uv sync
if errorlevel 1 exit /b 1

uv run uvicorn ecommerce_agent_ai.main:app --app-dir src --host 0.0.0.0 --port 8005 --reload

endlocal
