@echo off
setlocal
cd /d "%~dp0..\ai-python"

uv sync
if errorlevel 1 exit /b 1

uv run uvicorn ecommerce_agent_ai.main:app --app-dir src --host 0.0.0.0 --port 8005 --reload

endlocal
