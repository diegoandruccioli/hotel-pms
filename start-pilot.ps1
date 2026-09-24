#Requires -Version 5.1
<#
.SYNOPSIS
    Hotel PMS - Pilot Startup (PowerShell 5.1+, Windows)
.DESCRIPTION
    Startup script for a real hotel installation running on a single
    Windows PC, as opposed to start.ps1 (developer workflow: Vite dev
    server, npm install on every run, shuts the whole stack down on
    Ctrl+C). This script:
      - starts the production-hardened compose overlay
        (docker-compose.prod.yml: only the frontend container's port 80 is
        published to the host; every other service is internal-only), and
      - serves the app from the pre-built nginx frontend container, not
        Vite -- http://localhost, not http://localhost:5173, and
      - never stops the containers on its own exit. Every container
        already has `restart: unless-stopped` (docker-compose.yml); this
        window can be closed, the PC can reboot, and the stack comes back
        on its own. There is deliberately no Ctrl+C-triggers-shutdown
        behavior here, unlike start.ps1 -- a reception PC should not go
        dark because someone closed a terminal window by accident.
    To actually stop the stack: `docker compose down` (run manually).
.NOTES
    Encoding: ASCII-safe (no emoji/Unicode) for PS 5.1 compatibility.
    Backend Dockerfiles are single-stage (they COPY a pre-built jar), so a
    Gradle build on the host is still required before `docker compose
    build` -- this is a real constraint of the current Dockerfiles, not
    something this script can skip. See docs/OPERATIONS_RUNBOOK.md.
#>

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

# ── Resolve script root and lock working directory ────────────────────────────
$ScriptRoot = $PSScriptRoot
Set-Location -LiteralPath $ScriptRoot

# =============================================================================
#  LOGGING & OUTPUT HELPERS
# =============================================================================

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
    Write-Host ('-' * 60) -ForegroundColor DarkGray
}

function Write-Success { param([string]$Msg) Write-Log "  [OK]    $Msg" -Color Green  }
function Write-Info    { param([string]$Msg) Write-Log "  [i]     $Msg" -Color Yellow }
function Write-Fatal   { param([string]$Msg) Write-Log "  [ERR]   $Msg" -Color Red    }

function Show-Banner {
    Write-Host ''
    Write-Host '+----------------------------------------------------------+' -ForegroundColor Cyan
    Write-Host '|          Hotel PMS - Pilot Startup (PowerShell)          |' -ForegroundColor Cyan
    Write-Host '+----------------------------------------------------------+' -ForegroundColor Cyan
    Write-Host ''
}

function Stop-WithError {
    param([Parameter(Mandatory)][string]$Reason)
    Write-Host ''
    Write-Host '+----------------------------------------------------------+' -ForegroundColor Red
    Write-Host '|                      FATAL ERROR                        |' -ForegroundColor Red
    Write-Host '+----------------------------------------------------------+' -ForegroundColor Red
    Write-Fatal $Reason
    Write-Host ''
    Write-Host 'Press any key to close this window...' -ForegroundColor DarkYellow
    $null = $Host.UI.RawUI.ReadKey('NoEcho,IncludeKeyDown')
    exit 1
}

# =============================================================================
#  NATIVE COMMAND WRAPPERS
#
#  Same PowerShell 5.1 NativeCommandError.Statement workaround as start.ps1 --
#  see that file's comment for the full explanation. Kept identical here so
#  a future fix to one script is easy to port to the other.
# =============================================================================

function Invoke-Native {
    param(
        [Parameter(Mandatory)][string]$Description,
        [Parameter(Mandatory)][scriptblock]$Command,
        [switch]$AllowStderr
    )
    Set-StrictMode -Off
    $prev = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        if ($AllowStderr) { & $Command } else { & $Command 2>$null }
    } finally {
        $ErrorActionPreference = $prev
    }
    if ($LASTEXITCODE -ne 0) { Stop-WithError "$Description failed (exit code: $LASTEXITCODE)" }
}

function Invoke-NativeQuery {
    param([Parameter(Mandatory)][scriptblock]$Command)
    Set-StrictMode -Off
    $prev = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try { $out = & $Command 2>$null } finally { $ErrorActionPreference = $prev }
    return $out
}

# =============================================================================
#  PORT PRE-CHECK
# =============================================================================

function Assert-PortAvailable {
    param([Parameter(Mandatory)][int]$Port, [string]$ServiceLabel)
    $listener = Get-NetTCPConnection -LocalPort $Port -ErrorAction SilentlyContinue |
                Where-Object { $_.State -eq 'Listen' }
    if ($listener) {
        $procId   = $listener[0].OwningProcess
        $procName = (Get-Process -Id $procId -ErrorAction SilentlyContinue).ProcessName
        Stop-WithError "Port $Port is already in use by '$procName' (PID $procId). Required for: $ServiceLabel"
    }
}

# =============================================================================
#  DOCKER DESKTOP DETECTION & LAUNCH
#  (identical logic to start.ps1 -- see that file for the full comment trail)
# =============================================================================

function Start-DockerDesktop {
    Write-Info 'Checking if Docker daemon is active...'

    $null = Invoke-NativeQuery { docker ps }
    if ($LASTEXITCODE -eq 0) {
        Write-Success 'Docker is already running.'
        return
    }

    Write-Info 'Docker daemon is not active. Searching for Docker Desktop...'

    $candidatePaths = @(
        "$env:ProgramFiles\Docker\Docker\Docker Desktop.exe",
        "$env:ProgramFiles\Docker\Docker\Docker.exe",
        "${env:ProgramFiles(x86)}\Docker\Docker\Docker Desktop.exe",
        "$env:LOCALAPPDATA\Docker\Docker Desktop.exe",
        "$env:LOCALAPPDATA\Programs\Docker\Docker\Docker Desktop.exe"
    )

    try {
        $regPath = 'HKLM:\Software\Microsoft\Windows\CurrentVersion\Uninstall\Docker Desktop'
        $installLoc = (Get-ItemProperty -Path $regPath -ErrorAction SilentlyContinue).InstallLocation
        if ($installLoc) {
            $candidatePaths += Join-Path $installLoc 'Docker Desktop.exe'
            $candidatePaths += Join-Path $installLoc 'Docker\Docker.exe'
        }
    } catch {}

    $cmdDocker = Get-Command 'Docker Desktop.exe' -ErrorAction SilentlyContinue
    if ($cmdDocker) { $candidatePaths += $cmdDocker.Source }

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

    if (-not $dockerExe) {
        foreach ($path in $candidatePaths) {
            if (Test-Path $path) {
                $dockerExe = $path
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

    Write-Info "Starting Docker Desktop from: $dockerExe"
    Start-Process -FilePath $dockerExe

    $maxWaitSeconds = 90
    $pollInterval   = 3
    $elapsed        = 0

    Write-Info "Waiting for Docker daemon (timeout ${maxWaitSeconds}s)..."
    while ($elapsed -lt $maxWaitSeconds) {
        $null = Invoke-NativeQuery { docker ps }
        if ($LASTEXITCODE -eq 0) {
            Write-Success 'Docker daemon is now active.'
            return
        }
        Start-Sleep -Seconds $pollInterval
        $elapsed += $pollInterval
        Write-Host '.' -NoNewline -ForegroundColor DarkGray
    }
    Write-Host ''
    Stop-WithError "Docker daemon did not respond within ${maxWaitSeconds}s. Start Docker Desktop manually and retry."
}

# =============================================================================
#  CONTAINER HEALTH CHECK (via docker inspect)
# =============================================================================

function Wait-ForContainerHealthy {
    param(
        [Parameter(Mandatory)][string]$ServiceName,
        [Parameter(Mandatory)][string]$ContainerName,
        [int]$TimeoutSeconds = 120
    )

    Write-Info "Waiting for $ServiceName to become healthy (timeout ${TimeoutSeconds}s)..."
    $elapsed = 0
    while ($elapsed -lt $TimeoutSeconds) {
        $prev = $ErrorActionPreference
        $ErrorActionPreference = 'Continue'
        $status = (docker inspect --format='{{.State.Health.Status}}' $ContainerName 2>$null) -join ''
        $ErrorActionPreference = $prev

        if ($status -eq 'healthy') {
            Write-Host ''
            Write-Success "$ServiceName is healthy."
            return
        }
        Start-Sleep -Seconds 3
        $elapsed += 3
        Write-Host '.' -NoNewline -ForegroundColor DarkGray
    }
    Write-Host ''
    Stop-WithError "$ServiceName did not become healthy within ${TimeoutSeconds}s. Check logs: docker compose logs $ContainerName"
}

# =============================================================================
#  MAIN EXECUTION
# =============================================================================

try {
    Show-Banner

    # ── Detect if the stack is already running ────────────────────────────────
    $stackAlreadyRunning = $false
    $null = Invoke-NativeQuery { docker ps }
    if ($LASTEXITCODE -eq 0) {
        $feHealth = (Invoke-NativeQuery { docker inspect --format='{{.State.Health.Status}}' frontend }) -join ''
        if ($LASTEXITCODE -eq 0 -and $feHealth -eq 'healthy') {
            $stackAlreadyRunning = $true
            Write-Host ''
            Write-Success 'Hotel PMS is already running.'
        }
    }

    if (-not $stackAlreadyRunning) {
        # ── Step 0: Port availability ─────────────────────────────────────────
        # docker-compose.prod.yml resets every port to the host except the
        # frontend's (:80) -- that is the only host port that matters here.
        Write-Step '0/6' 'Pre-flight port check'
        Assert-PortAvailable -Port 80 -ServiceLabel 'Hotel PMS (frontend)'
        Write-Success 'Port 80 is available.'

        # ── Step 1: Docker ────────────────────────────────────────────────────
        Write-Step '1/6' 'Ensuring Docker Desktop is running'
        Start-DockerDesktop

        # ── Step 2: Secrets bootstrap ─────────────────────────────────────────
        # Generates INTERNAL_HMAC_SECRET, JWT_SECRET, the 5 per-service DB
        # passwords, and (since GAP-29) the 6 PII/credential encryption
        # key+salt pairs. All idempotent -- safe to run on every startup.
        Write-Step '2/6' 'Secrets bootstrap'
        $hmacScript = Join-Path $ScriptRoot 'setup-hmac-secret.ps1'
        if (-not (Test-Path $hmacScript)) {
            Stop-WithError "Secrets setup script not found at: $hmacScript"
        }
        Invoke-Native 'Secrets setup' { & $hmacScript } -AllowStderr
        Write-Success 'Secrets are ready.'

        $envFile = Join-Path $ScriptRoot '.env'
        if (-not (Test-Path $envFile)) {
            Stop-WithError ".env file not found at $envFile -- secrets setup may have failed silently."
        }

        # ── Step 3: Gradle build ──────────────────────────────────────────────
        # Required because every backend Dockerfile is single-stage: it COPYs
        # a pre-built jar from build/libs/ rather than building it in a Docker
        # build stage. There is no way to skip this step without rewriting
        # those Dockerfiles (out of scope here) -- `docker compose build`
        # alone would silently reuse a stale jar or fail outright.
        Write-Step '3/6' 'Building microservices (Gradle)'
        $gradlew = Join-Path $ScriptRoot 'gradlew.bat'
        if (-not (Test-Path $gradlew)) {
            Stop-WithError "gradlew.bat not found at: $gradlew"
        }
        Invoke-Native 'Gradle build' { & $gradlew clean build -x test } -AllowStderr
        Write-Success 'All microservices built successfully.'

        # ── Step 4: Docker Compose (production overlay) ──────────────────────
        # -f docker-compose.prod.yml resets every port except the frontend's
        # (:80) to the host -- api-gateway, postgres, redis, and the whole
        # observability stack become internal-only. --profile observability
        # is required even in this overlay: a profile-gated service stays
        # off unless the flag is passed, an override file does not re-enable it.
        Write-Step '4/6' 'Starting Docker infrastructure (production-hardened ports)'
        Invoke-Native 'docker compose up' {
            docker compose --env-file $envFile -f docker-compose.yml -f docker-compose.prod.yml --profile observability up -d --build
        } -AllowStderr
        Write-Success 'All containers are starting.'

        # ── Step 5: Health checks ──────────────────────────────────────────────
        Write-Step '5/6' 'Waiting for the application to come up'
        Wait-ForContainerHealthy -ServiceName 'Config Server' -ContainerName 'config-server' -TimeoutSeconds 120
        Wait-ForContainerHealthy -ServiceName 'API Gateway'   -ContainerName 'api-gateway'    -TimeoutSeconds 180
        Wait-ForContainerHealthy -ServiceName 'Hotel PMS (frontend)' -ContainerName 'frontend' -TimeoutSeconds 60
    }

    # ── Step 6: Open the browser ────────────────────────────────────────────────
    Write-Step '6/6' 'Opening Hotel PMS'
    Start-Process -FilePath 'cmd.exe' -ArgumentList '/c', 'start', '', 'http://localhost'

    Write-Host ''
    Write-Host '+----------------------------------------------------------+' -ForegroundColor Green
    Write-Host '|                    Hotel PMS is ready                    |' -ForegroundColor Green
    Write-Host '+----------------------------------------------------------+' -ForegroundColor Green
    Write-Host ''
    Write-Log '  Hotel PMS is running at: http://localhost' -Color Green
    Write-Log '  It will keep running after this window is closed --' -Color Yellow
    Write-Log '  every container restarts on its own (restart: unless-stopped)' -Color Yellow
    Write-Log '  and survives a PC reboot.' -Color Yellow
    Write-Host ''
    Write-Log '  To stop it (only if you really need to): docker compose down' -Color DarkGray
    Write-Host ''
    Write-Host 'Press any key to close this window (Hotel PMS keeps running)...' -ForegroundColor DarkYellow
    $null = $Host.UI.RawUI.ReadKey('NoEcho,IncludeKeyDown')
}
catch {
    Write-Host ''
    Write-Host '+----------------------------------------------------------+' -ForegroundColor Red
    Write-Host '|                      FATAL ERROR                        |' -ForegroundColor Red
    Write-Host '+----------------------------------------------------------+' -ForegroundColor Red
    Write-Fatal "Startup aborted: $($_.Exception.Message)"
    Write-Host ''
    Write-Host 'Press any key to close this window...' -ForegroundColor DarkYellow
    $null = $Host.UI.RawUI.ReadKey('NoEcho,IncludeKeyDown')
}
