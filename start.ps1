#Requires -Version 5.1
<#
.SYNOPSIS
    Enterprise Hotel PMS – One-Click Startup (PowerShell 5.1+)
.DESCRIPTION
    Bootstraps Docker Desktop, generates HMAC secrets, starts all
    microservices via docker compose, waits for health checks, then
    launches the React/Vite frontend dev-server with automatic
    browser opener. Implements fatal error handling, timestamped
    logging, port pre-checks, and graceful cleanup on exit.
#>

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

# ── Resolve script root and lock working directory ────────────────────────────
$ScriptRoot = $PSScriptRoot
Set-Location -LiteralPath $ScriptRoot

# ── State flag: tracks whether docker compose was started ─────────────────────
$script:ComposeStarted = $false

# ═══════════════════════════════════════════════════════════════════════════════
#  LOGGING & OUTPUT HELPERS
# ═══════════════════════════════════════════════════════════════════════════════

function Write-Log {
    param(
        [Parameter(Mandatory)][string]$Message,
        [ConsoleColor]$Color = 'White'
    )
    $timestamp = Get-Date -Format 'HH:mm:ss'
    Write-Host "[$timestamp] $Message" -ForegroundColor $Color
}

function Write-Step {
    param(
        [Parameter(Mandatory)][string]$Number,
        [Parameter(Mandatory)][string]$Label
    )
    Write-Host ''
    Write-Log "[$Number] $Label" -Color Cyan
    Write-Host ('─' * 60) -ForegroundColor DarkGray
}

function Write-Success  { param([string]$Msg) Write-Log "  ✅  $Msg" -Color Green  }
function Write-Info     { param([string]$Msg) Write-Log "  ℹ️   $Msg" -Color Yellow }
function Write-Fatal    { param([string]$Msg) Write-Log "  ❌  $Msg" -Color Red    }

function Show-Banner {
    Write-Host ''
    Write-Host '╔══════════════════════════════════════════════════════════╗' -ForegroundColor Cyan
    Write-Host '║       🏨  Hotel PMS – One-Click Startup (PowerShell)    ║' -ForegroundColor Cyan
    Write-Host '╚══════════════════════════════════════════════════════════╝' -ForegroundColor Cyan
    Write-Host ''
}

function Stop-WithError {
    param([Parameter(Mandatory)][string]$Reason)
    Write-Host ''
    Write-Host '╔══════════════════════════════════════════════════════════╗' -ForegroundColor Red
    Write-Host '║                    FATAL ERROR                          ║' -ForegroundColor Red
    Write-Host '╚══════════════════════════════════════════════════════════╝' -ForegroundColor Red
    Write-Fatal $Reason
    Write-Host ''
    Write-Host 'Press any key to close this window...' -ForegroundColor DarkYellow
    $null = $Host.UI.RawUI.ReadKey('NoEcho,IncludeKeyDown')
    exit 1
}

# ═══════════════════════════════════════════════════════════════════════════════
#  PORT PRE-CHECK
# ═══════════════════════════════════════════════════════════════════════════════

function Assert-PortAvailable {
    param([Parameter(Mandatory)][int]$Port, [string]$ServiceLabel)
    $listener = Get-NetTCPConnection -LocalPort $Port -ErrorAction SilentlyContinue |
                Where-Object { $_.State -eq 'Listen' }
    if ($listener) {
        $procId = $listener[0].OwningProcess
        $procName = (Get-Process -Id $procId -ErrorAction SilentlyContinue).ProcessName
        Stop-WithError "Port $Port is already in use by '$procName' (PID $procId). Required for: $ServiceLabel"
    }
}

# ═══════════════════════════════════════════════════════════════════════════════
#  DOCKER DESKTOP DETECTION & LAUNCH
# ═══════════════════════════════════════════════════════════════════════════════

function Start-DockerDesktop {
    Write-Info 'Checking if Docker daemon is active...'

    # Fast path: Docker already running
    $null = docker ps 2>&1
    if ($LASTEXITCODE -eq 0) {
        Write-Success 'Docker is already running!'
        return
    }

    Write-Info 'Docker daemon is not active. Searching for Docker Desktop...'

    # ── Build candidate path list ─────────────────────────────────────────────
    $candidatePaths = @(
        "$env:ProgramFiles\Docker\Docker\Docker Desktop.exe",
        "$env:ProgramFiles\Docker\Docker\Docker.exe",
        "${env:ProgramFiles(x86)}\Docker\Docker\Docker Desktop.exe",
        "$env:LOCALAPPDATA\Docker\Docker Desktop.exe",
        "$env:LOCALAPPDATA\Programs\Docker\Docker\Docker Desktop.exe"
    )

    # Registry lookup
    try {
        $regPath = 'HKLM:\Software\Microsoft\Windows\CurrentVersion\Uninstall\Docker Desktop'
        $installLoc = (Get-ItemProperty -Path $regPath -ErrorAction SilentlyContinue).InstallLocation
        if ($installLoc) {
            $candidatePaths += Join-Path $installLoc 'Docker Desktop.exe'
            $candidatePaths += Join-Path $installLoc 'Docker\Docker.exe'
        }
    } catch {}

    # Get-Command fallback
    $cmdDocker = Get-Command 'Docker Desktop.exe' -ErrorAction SilentlyContinue
    if ($cmdDocker) { $candidatePaths += $cmdDocker.Source }

    # ── Try cached path first ─────────────────────────────────────────────────
    $cacheDir  = Join-Path $env:APPDATA 'HotelPMS'
    $cacheFile = Join-Path $cacheDir 'docker-path.txt'
    $dockerExe = $null

    if (Test-Path $cacheFile) {
        $cached = (Get-Content $cacheFile -ErrorAction SilentlyContinue).Trim()
        if ($cached -and (Test-Path $cached)) {
            Write-Info "Using cached Docker path: $cached"
            $dockerExe = $cached
        }
    }

    # ── Scan candidates if cache miss ─────────────────────────────────────────
    if (-not $dockerExe) {
        foreach ($path in $candidatePaths) {
            if (Test-Path $path) {
                $dockerExe = $path
                # Persist to cache
                if (-not (Test-Path $cacheDir)) { New-Item -ItemType Directory -Path $cacheDir -Force | Out-Null }
                Set-Content -Path $cacheFile -Value $dockerExe -Encoding UTF8
                Write-Info "Found Docker Desktop at: $dockerExe"
                break
            }
        }
    }

    if (-not $dockerExe) {
        Stop-WithError 'Docker Desktop executable not found. Install it from https://www.docker.com/products/docker-desktop'
    }

    # ── Launch and poll ───────────────────────────────────────────────────────
    Write-Info "Starting Docker from: $dockerExe"
    Start-Process -FilePath $dockerExe

    $maxWaitSeconds = 90
    $pollInterval   = 3
    $elapsed        = 0

    Write-Info "Waiting for Docker daemon (timeout ${maxWaitSeconds}s)..."
    while ($elapsed -lt $maxWaitSeconds) {
        $null = docker ps 2>&1
        if ($LASTEXITCODE -eq 0) {
            Write-Success 'Docker daemon is now active!'
            return
        }
        Start-Sleep -Seconds $pollInterval
        $elapsed += $pollInterval
        Write-Host '.' -NoNewline -ForegroundColor DarkGray
    }
    Write-Host ''
    Stop-WithError "Docker daemon did not respond within ${maxWaitSeconds}s. Start Docker Desktop manually and retry."
}

# ═══════════════════════════════════════════════════════════════════════════════
#  CONTAINER HEALTH CHECK (via docker inspect — management port is internal-only)
# ═══════════════════════════════════════════════════════════════════════════════

function Wait-ForContainerHealthy {
    param(
        [Parameter(Mandatory)][string]$ServiceName,
        [Parameter(Mandatory)][string]$ContainerName,
        [int]$TimeoutSeconds = 120
    )

    Write-Info "Waiting for $ServiceName to become healthy (timeout ${TimeoutSeconds}s)..."
    $elapsed = 0
    while ($elapsed -lt $TimeoutSeconds) {
        $status = (docker inspect --format='{{.State.Health.Status}}' $ContainerName 2>$null) -join ''
        if ($status -eq 'healthy') {
            Write-Host ''
            Write-Success "$ServiceName is healthy!"
            return
        }
        Start-Sleep -Seconds 3
        $elapsed += 3
        Write-Host '.' -NoNewline -ForegroundColor DarkGray
    }
    Write-Host ''
    Stop-WithError "$ServiceName did not become healthy within ${TimeoutSeconds}s. Check container logs with: docker compose logs $ContainerName"
}

# ═══════════════════════════════════════════════════════════════════════════════
#  CLEANUP HANDLER
# ═══════════════════════════════════════════════════════════════════════════════

function Invoke-Cleanup {
    if ($script:ComposeStarted) {
        Write-Host ''
        Write-Log '🛑 Shutting down Docker containers...' -Color Yellow
        Set-Location -LiteralPath $ScriptRoot
        docker compose down 2>&1 | Out-Null
        Write-Log '✅ Containers stopped. Goodbye!' -Color Green
    }
    Write-Host ''
    Write-Host 'Press any key to close this window...' -ForegroundColor DarkYellow
    $null = $Host.UI.RawUI.ReadKey('NoEcho,IncludeKeyDown')
}

# ═══════════════════════════════════════════════════════════════════════════════
#  MAIN EXECUTION
# ═══════════════════════════════════════════════════════════════════════════════

$browserJob = $null
try {
    Show-Banner

    # ── Step 0: Port availability ─────────────────────────────────────────────
    Write-Step '0/7' 'Pre-flight port checks'
    Assert-PortAvailable -Port 8888 -ServiceLabel 'Config Server'
    Assert-PortAvailable -Port 8080 -ServiceLabel 'API Gateway'
    Assert-PortAvailable -Port 5173 -ServiceLabel 'Vite Dev Server'
    Assert-PortAvailable -Port 5432 -ServiceLabel 'PostgreSQL'
    Assert-PortAvailable -Port 6379 -ServiceLabel 'Redis'
    Write-Success 'All required ports are available.'

    # ── Step 1: Docker ────────────────────────────────────────────────────────
    Write-Step '1/7' 'Ensuring Docker Desktop is running'
    Start-DockerDesktop

    # ── Step 2: HMAC Secret ───────────────────────────────────────────────────
    Write-Step '2/7' 'HMAC secret bootstrap'
    $hmacScript = Join-Path $ScriptRoot 'setup-hmac-secret.ps1'
    if (-not (Test-Path $hmacScript)) {
        Stop-WithError "HMAC setup script not found at: $hmacScript"
    }
    try {
        & $hmacScript
        if ($LASTEXITCODE -and $LASTEXITCODE -ne 0) {
            throw "setup-hmac-secret.ps1 exited with code $LASTEXITCODE"
        }
    } catch {
        Stop-WithError "HMAC secret setup failed: $_"
    }
    Write-Success 'HMAC secret is ready.'

    # ── Step 3: Docker Compose ────────────────────────────────────────────────
    Write-Step '3/7' 'Starting Docker infrastructure'
    $envFile = Join-Path $ScriptRoot '.env'
    if (-not (Test-Path $envFile)) {
        Stop-WithError ".env file not found at $envFile — HMAC setup may have failed silently."
    }
    docker compose --env-file $envFile up -d --build 2>&1 | ForEach-Object { Write-Host "  $_" -ForegroundColor DarkGray }
    if ($LASTEXITCODE -ne 0) {
        Stop-WithError "docker compose up failed with exit code $LASTEXITCODE."
    }
    $script:ComposeStarted = $true
    Write-Success 'All containers are starting.'

    # ── Step 4: Config Server health ──────────────────────────────────────────
    Write-Step '4/7' 'Waiting for Config Server'
    Wait-ForContainerHealthy -ServiceName 'Config Server' -ContainerName 'config-server' -TimeoutSeconds 120

    # ── Step 5: API Gateway health ────────────────────────────────────────────
    Write-Step '5/7' 'Waiting for API Gateway'
    Wait-ForContainerHealthy -ServiceName 'API Gateway' -ContainerName 'api-gateway' -TimeoutSeconds 180

    # ── Step 6: Frontend ──────────────────────────────────────────────────────
    Write-Step '6/7' 'Installing frontend dependencies'
    $frontendDir = Join-Path $ScriptRoot 'frontend'
    if (-not (Test-Path $frontendDir)) {
        Stop-WithError "frontend/ directory not found at: $frontendDir"
    }
    Set-Location -LiteralPath $frontendDir
    npm install --silent 2>&1 | ForEach-Object { Write-Host "  $_" -ForegroundColor DarkGray }
    if ($LASTEXITCODE -ne 0) {
        Stop-WithError "npm install failed with exit code $LASTEXITCODE."
    }
    Write-Success 'Frontend dependencies installed.'

    # ── Step 7: Launch Vite + browser ─────────────────────────────────────────
    Write-Step '7/7' 'Launching Vite dev server'

    # Background job: wait for port 3000 to respond, then open browser
    $browserJob = Start-Job -ScriptBlock {
        $maxWait = 30
        $waited  = 0
        while ($waited -lt $maxWait) {
            try {
                $tcp = New-Object System.Net.Sockets.TcpClient
                $tcp.Connect('127.0.0.1', 5173)
                $tcp.Close()
                Start-Process 'http://localhost:5173'
                return
            } catch {
                Start-Sleep -Seconds 1
                $waited++
            }
        }
    }

    Write-Log '  💡 Press Ctrl+C to stop the server and shut down Docker.' -Color Yellow
    Write-Log '  🚀 Vite dev server starting on http://localhost:5173' -Color Green
    Write-Host ''

    npm run dev
}
catch {
    Write-Host ''
    Write-Host '╔══════════════════════════════════════════════════════════╗' -ForegroundColor Red
    Write-Host '║                    FATAL ERROR                          ║' -ForegroundColor Red
    Write-Host '╚══════════════════════════════════════════════════════════╝' -ForegroundColor Red
    Write-Fatal "Startup aborted: $_"
}
finally {
    # Clean up browser job if it exists
    if ($browserJob) { Remove-Job -Job $browserJob -Force -ErrorAction SilentlyContinue }
    Invoke-Cleanup
}