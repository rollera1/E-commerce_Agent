@echo off
setlocal

if not defined ECOMMERCE_JAVA_HOME set "ECOMMERCE_JAVA_HOME=D:\jdks\jdk25"
if not exist "%ECOMMERCE_JAVA_HOME%\bin\java.exe" (
  echo JDK 25 was not found at %ECOMMERCE_JAVA_HOME%.
  echo Set ECOMMERCE_JAVA_HOME to your JDK 25 directory and retry.
  exit /b 1
)

set "JAVA_HOME=%ECOMMERCE_JAVA_HOME%"
set "PATH=%JAVA_HOME%\bin;%PATH%"
cd /d "%~dp0..\backend-java"

echo This process uses:
java -version
mvn spring-boot:run

endlocal
