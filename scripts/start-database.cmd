@echo off
setlocal
set "PROJECT_DIR=%~dp0.."

docker compose --env-file "%PROJECT_DIR%\.env" -f "%PROJECT_DIR%\deploy\compose.yaml" up -d mysql 2>nul
if errorlevel 1 (
  echo Could not use .env; starting with safe local defaults from compose.yaml.
  docker compose -f "%PROJECT_DIR%\deploy\compose.yaml" up -d mysql
)

endlocal
