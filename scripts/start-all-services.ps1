[CmdletBinding()]
param(
    [int]$StartupTimeoutSeconds = 90
)

$ErrorActionPreference = "Stop"
$projectDir = Split-Path -Parent $PSScriptRoot
$runtimeDir = Join-Path $projectDir ".runtime"
$logDir = Join-Path $runtimeDir "logs"

New-Item -ItemType Directory -Force -Path $runtimeDir, $logDir | Out-Null

function Test-LocalPort {
    param([int]$Port)

    $client = [System.Net.Sockets.TcpClient]::new()
    try {
        $connection = $client.BeginConnect("127.0.0.1", $Port, $null, $null)
        if (-not $connection.AsyncWaitHandle.WaitOne(1000)) { return $false }
        $client.EndConnect($connection)
        return $client.Connected
    }
    catch {
        return $false
    }
    finally {
        $client.Dispose()
    }
}

function Wait-LocalPort {
    param(
        [string]$Name,
        [int]$Port,
        [int]$TimeoutSeconds
    )

    $deadline = [DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
    while ([DateTime]::UtcNow -lt $deadline) {
        if (Test-LocalPort -Port $Port) {
            Write-Host "[OK] $Name is listening on port $Port." -ForegroundColor Green
            return
        }
        Start-Sleep -Milliseconds 500
    }

    $errorLog = Join-Path $logDir "$Name.error.log"
    throw "$Name did not start on port $Port within $TimeoutSeconds seconds. Check $errorLog"
}

function Start-ProjectService {
    param(
        [string]$Name,
        [int]$Port,
        [string]$ScriptName
    )

    if (Test-LocalPort -Port $Port) {
        Write-Host "[SKIP] $Name is already running on port $Port." -ForegroundColor Yellow
        return
    }

    $scriptPath = Join-Path $PSScriptRoot $ScriptName
    if (-not (Test-Path -LiteralPath $scriptPath)) {
        throw "Startup script not found: $scriptPath"
    }

    $stdoutLog = Join-Path $logDir "$Name.log"
    $stderrLog = Join-Path $logDir "$Name.error.log"
    $process = Start-Process `
        -FilePath $env:ComSpec `
        -ArgumentList @("/d", "/c", "call `"$scriptPath`"") `
        -WorkingDirectory $projectDir `
        -WindowStyle Hidden `
        -RedirectStandardOutput $stdoutLog `
        -RedirectStandardError $stderrLog `
        -PassThru

    Set-Content -LiteralPath (Join-Path $runtimeDir "$Name.pid") -Value $process.Id
    Write-Host "[START] $Name (PID $($process.Id)); logs: $stdoutLog" -ForegroundColor Cyan
}

Write-Host "Starting E-commerce Agent local services..." -ForegroundColor Cyan

# Ensure the database tunnel is available before starting Java.
Start-ProjectService -Name "database" -Port 3307 -ScriptName "start-database.cmd"
Wait-LocalPort -Name "database" -Port 3307 -TimeoutSeconds $StartupTimeoutSeconds

# Start Python and frontend independently.
Start-ProjectService -Name "python" -Port 8005 -ScriptName "start-python.cmd"
Start-ProjectService -Name "frontend" -Port 5175 -ScriptName "start-frontend.cmd"
Wait-LocalPort -Name "python" -Port 8005 -TimeoutSeconds $StartupTimeoutSeconds
Wait-LocalPort -Name "frontend" -Port 5175 -TimeoutSeconds $StartupTimeoutSeconds

# Java requires the database and Python services.
Start-ProjectService -Name "backend" -Port 8085 -ScriptName "start-backend.cmd"
Wait-LocalPort -Name "backend" -Port 8085 -TimeoutSeconds $StartupTimeoutSeconds

Write-Host ""
Write-Host "All four services are running." -ForegroundColor Green
Write-Host "Frontend: http://localhost:5175"
Write-Host "Java:    http://localhost:8085/api/health"
Write-Host "Python:  http://localhost:8005/health"
Write-Host "Logs:    $logDir"
Write-Host ""
Write-Host "Run .\scripts\check-services.ps1 to verify the complete chain."
