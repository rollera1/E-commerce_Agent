$ErrorActionPreference = "Stop"

Write-Host "Checking Java service..." -ForegroundColor Cyan
$java = Invoke-RestMethod -Uri "http://localhost:8085/api/health"
$java | ConvertTo-Json

Write-Host "Checking Java -> Python integration..." -ForegroundColor Cyan
$integration = Invoke-RestMethod -Uri "http://localhost:8085/api/health/python"
$integration | ConvertTo-Json -Depth 4

if ($java.status -ne "ok" -or $integration.downstream.status -ne "ok") {
    throw "One or more services are unhealthy."
}

Write-Host "All service checks passed." -ForegroundColor Green
