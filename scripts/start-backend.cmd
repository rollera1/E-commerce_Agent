@echo off
setlocal

set "PROJECT_DIR=%~dp0.."
if exist "%PROJECT_DIR%\.env" (
  for /f "usebackq eol=# tokens=1,* delims==" %%A in ("%PROJECT_DIR%\.env") do (
    if not "%%A"=="" set "%%A=%%B"
  )
)

if not defined ECOMMERCE_JAVA_HOME set "ECOMMERCE_JAVA_HOME=D:\jdks\jdk25"
if not exist "%ECOMMERCE_JAVA_HOME%\bin\java.exe" (
  echo JDK 25 was not found at %ECOMMERCE_JAVA_HOME%.
  echo Set ECOMMERCE_JAVA_HOME to your JDK 25 directory and retry.
  exit /b 1
)

set "JAVA_HOME=%ECOMMERCE_JAVA_HOME%"
set "PATH=%JAVA_HOME%\bin;%PATH%"

set "MAVEN_CMD="
if defined ECOMMERCE_MAVEN_HOME if exist "%ECOMMERCE_MAVEN_HOME%\bin\mvn.cmd" set "MAVEN_CMD=%ECOMMERCE_MAVEN_HOME%\bin\mvn.cmd"
if not defined MAVEN_CMD (
  where mvn.cmd >nul 2>&1
  if not errorlevel 1 set "MAVEN_CMD=mvn.cmd"
)
if not defined MAVEN_CMD (
  for /f "delims=" %%I in ('dir /b /s /a:-d "%USERPROFILE%\.m2\wrapper\dists\mvn.cmd" 2^>nul') do set "MAVEN_CMD=%%I"
)
if not defined MAVEN_CMD (
  echo Maven was not found on PATH or in the local Maven wrapper cache.
  echo Set ECOMMERCE_MAVEN_HOME to the Maven installation directory and retry.
  exit /b 1
)

cd /d "%PROJECT_DIR%\backend-java"

echo This process uses:
java -version
echo Maven: %MAVEN_CMD%
call "%MAVEN_CMD%" spring-boot:run

endlocal
