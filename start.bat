@echo off
setlocal enabledelayedexpansion

:: ═══════════════════════════════════════════════════════════════════════════════
::  Enterprise Hotel PMS – One-Click Startup (Windows CMD)
::  Bulletproof: timestamps, colour-coded output, port pre-checks,
::  hard-fail Docker timeout, HMAC error capture, cleanup trap.
:: ═══════════════════════════════════════════════════════════════════════════════

cd /d "%~dp0"

:: ── Colour Codes ─────────────────────────────────────────────────────────────
set "ESC="
set "C_RST=[0m"
set "C_RED=[91m"
set "C_GRN=[92m"
set "C_YLW=[93m"
set "C_CYN=[96m"
set "C_GRY=[90m"

:: ── State ────────────────────────────────────────────────────────────────────
set "COMPOSE_STARTED=0"

:: ═══════════════════════════════════════════════════════════════════════════════
::  BANNER
:: ═══════════════════════════════════════════════════════════════════════════════
echo.
echo %C_CYN%+----------------------------------------------------------+%C_RST%
echo %C_CYN%^|       Hotel PMS  One-Click Startup (CMD)                 ^|%C_RST%
echo %C_CYN%+----------------------------------------------------------+%C_RST%
echo.

:: ═══════════════════════════════════════════════════════════════════════════════
::  STEP 0 — Port pre-checks
:: ═══════════════════════════════════════════════════════════════════════════════
call :LOG_STEP "0/7" "Pre-flight port checks"

call :CHECK_PORT 8888 "Config Server"
if !ERRORLEVEL! neq 0 goto FATAL
call :CHECK_PORT 8080 "API Gateway"
if !ERRORLEVEL! neq 0 goto FATAL
call :CHECK_PORT 5173 "Vite Dev Server"
if !ERRORLEVEL! neq 0 goto FATAL
call :CHECK_PORT 5432 "PostgreSQL"
if !ERRORLEVEL! neq 0 goto FATAL
call :CHECK_PORT 6379 "Redis"
if !ERRORLEVEL! neq 0 goto FATAL

call :LOG_OK "All required ports are available."

:: ═══════════════════════════════════════════════════════════════════════════════
::  STEP 1 — Ensure Docker is running
:: ═══════════════════════════════════════════════════════════════════════════════
call :LOG_STEP "1/7" "Ensuring Docker Desktop is running"

docker ps >nul 2>&1
if !ERRORLEVEL! equ 0 (
    call :LOG_OK "Docker is already running!"
    goto DOCKER_READY
)

call :LOG_INFO "Docker daemon is not active. Searching for Docker Desktop..."

:: Try cached path first
set "CACHE_DIR=%APPDATA%\HotelPMS"
set "CACHE_FILE=!CACHE_DIR!\docker-path.txt"
set "DOCKER_EXE="

if exist "!CACHE_FILE!" (
    for /f "usebackq delims=" %%i in ("!CACHE_FILE!") do set "DOCKER_EXE=%%i"
    if exist "!DOCKER_EXE!" (
        call :LOG_INFO "Using cached path: !DOCKER_EXE!"
        goto START_DOCKER
    )
    set "DOCKER_EXE="
)

:: Scan common installation paths
for %%P in (
    "C:\Program Files\Docker\Docker\Docker Desktop.exe"
    "C:\Program Files\Docker\Docker\Docker.exe"
    "C:\Program Files (x86)\Docker\Docker\Docker Desktop.exe"
    "%LOCALAPPDATA%\Docker\Docker Desktop.exe"
    "%LOCALAPPDATA%\Programs\Docker\Docker\Docker Desktop.exe"
) do (
    if exist %%P (
        set "DOCKER_EXE=%%~P"
        goto SAVE_DOCKER_CACHE
    )
)

:: Registry lookup
for /f "tokens=2*" %%A in ('reg query "HKLM\Software\Microsoft\Windows\CurrentVersion\Uninstall\Docker Desktop" /v "InstallLocation" 2^>nul ^| find "InstallLocation"') do (
    set "INSTALL_LOC=%%B"
    if exist "%%B\Docker Desktop.exe" (
        set "DOCKER_EXE=%%B\Docker Desktop.exe"
        goto SAVE_DOCKER_CACHE
    )
    if exist "%%B\Docker\Docker.exe" (
        set "DOCKER_EXE=%%B\Docker\Docker.exe"
        goto SAVE_DOCKER_CACHE
    )
)

:: where.exe fallback
for /f "delims=" %%W in ('where "Docker Desktop.exe" 2^>nul') do (
    set "DOCKER_EXE=%%W"
    goto SAVE_DOCKER_CACHE
)

set "FATAL_MSG=Docker Desktop executable not found. Install from https://www.docker.com/products/docker-desktop"
goto FATAL

:SAVE_DOCKER_CACHE
if not exist "!CACHE_DIR!" mkdir "!CACHE_DIR!"
(echo !DOCKER_EXE!)> "!CACHE_FILE!"

:START_DOCKER
call :LOG_INFO "Starting Docker from: !DOCKER_EXE!"
start "" "!DOCKER_EXE!"

set /a "MAX_WAIT=90"
set /a "POLL=3"
set /a "ELAPSED_D=0"
call :LOG_INFO "Waiting for Docker daemon (timeout !MAX_WAIT!s)..."

:POLL_DOCKER
if !ELAPSED_D! geq !MAX_WAIT! (
    set "FATAL_MSG=Docker daemon did not respond within !MAX_WAIT!s. Start Docker Desktop manually and retry."
    goto FATAL
)
docker ps >nul 2>&1
if !ERRORLEVEL! equ 0 (
    echo.
    call :LOG_OK "Docker daemon is now active!"
    goto DOCKER_READY
)
<nul set /p "=." 2>nul
timeout /t !POLL! /nobreak >nul
set /a "ELAPSED_D+=!POLL!"
goto POLL_DOCKER

:DOCKER_READY

:: ═══════════════════════════════════════════════════════════════════════════════
::  STEP 2 — HMAC Secret bootstrap
:: ═══════════════════════════════════════════════════════════════════════════════
call :LOG_STEP "2/7" "HMAC secret bootstrap"

if not exist "%~dp0setup-hmac-secret.ps1" (
    set "FATAL_MSG=setup-hmac-secret.ps1 not found in project root."
    goto FATAL
)

powershell -ExecutionPolicy Bypass -NoProfile -File "%~dp0setup-hmac-secret.ps1"
if !ERRORLEVEL! neq 0 (
    set "FATAL_MSG=HMAC secret setup failed (exit code !ERRORLEVEL!). Check setup-hmac-secret.ps1 output above."
    goto FATAL
)
call :LOG_OK "HMAC secret is ready."

:: ═══════════════════════════════════════════════════════════════════════════════
::  STEP 3 — Docker Compose
:: ═══════════════════════════════════════════════════════════════════════════════
call :LOG_STEP "3/7" "Starting Docker infrastructure"

if not exist "%~dp0.env" (
    set "FATAL_MSG=.env file not found — HMAC setup may have failed silently."
    goto FATAL
)

docker compose --env-file .env up -d --build
if !ERRORLEVEL! neq 0 (
    set "FATAL_MSG=docker compose up failed with exit code !ERRORLEVEL!."
    goto FATAL
)
set "COMPOSE_STARTED=1"
call :LOG_OK "All containers are starting."

:: ═══════════════════════════════════════════════════════════════════════════════
::  STEP 4 — Config Server health check
:: ═══════════════════════════════════════════════════════════════════════════════
call :LOG_STEP "4/7" "Waiting for Config Server"

set /a "ELAPSED_H=0"
set /a "TIMEOUT_H=120"
:WAIT_CONFIG
if !ELAPSED_H! geq !TIMEOUT_H! (
    set "FATAL_MSG=Config Server did not become healthy within !TIMEOUT_H!s. Run: docker compose logs config-server"
    goto FATAL_WITH_CLEANUP
)
curl -sf http://localhost:8888/actuator/health/liveness >nul 2>&1
if !ERRORLEVEL! equ 0 (
    echo.
    call :LOG_OK "Config Server is healthy!"
    goto CONFIG_READY
)
<nul set /p "=." 2>nul
timeout /t 3 /nobreak >nul
set /a "ELAPSED_H+=3"
goto WAIT_CONFIG
:CONFIG_READY

:: ═══════════════════════════════════════════════════════════════════════════════
::  STEP 5 — API Gateway health check
:: ═══════════════════════════════════════════════════════════════════════════════
call :LOG_STEP "5/7" "Waiting for API Gateway"

set /a "ELAPSED_H=0"
set /a "TIMEOUT_H=180"
:WAIT_GW
if !ELAPSED_H! geq !TIMEOUT_H! (
    set "FATAL_MSG=API Gateway did not become healthy within !TIMEOUT_H!s. Run: docker compose logs api-gateway"
    goto FATAL_WITH_CLEANUP
)
curl -sf http://localhost:8080/actuator/health/liveness >nul 2>&1
if !ERRORLEVEL! equ 0 (
    echo.
    call :LOG_OK "API Gateway is healthy!"
    goto GW_READY
)
<nul set /p "=." 2>nul
timeout /t 3 /nobreak >nul
set /a "ELAPSED_H+=3"
goto WAIT_GW
:GW_READY

:: ═══════════════════════════════════════════════════════════════════════════════
::  STEP 6 — Frontend dependencies
:: ═══════════════════════════════════════════════════════════════════════════════
call :LOG_STEP "6/7" "Installing frontend dependencies"

pushd "%~dp0frontend" 2>nul
if !ERRORLEVEL! neq 0 (
    set "FATAL_MSG=frontend\ directory not found."
    goto FATAL_WITH_CLEANUP
)

call npm install --silent
if !ERRORLEVEL! neq 0 (
    popd
    set "FATAL_MSG=npm install failed with exit code !ERRORLEVEL!."
    goto FATAL_WITH_CLEANUP
)
call :LOG_OK "Frontend dependencies installed."

:: ═══════════════════════════════════════════════════════════════════════════════
::  STEP 7 — Vite + Browser
:: ═══════════════════════════════════════════════════════════════════════════════
call :LOG_STEP "7/7" "Launching Vite dev server"

:: Background: poll port 5173 then open browser
start /b "" cmd /c "for /l %%W in (1,1,30) do (curl -sf http://localhost:5173 >nul 2>&1 && (start http://localhost:5173 & exit /b 0) || timeout /t 1 /nobreak >nul)"

call :LOG_INFO "Press Ctrl+C to stop the server and shut down Docker."
call :LOG_OK  "Vite dev server starting on http://localhost:5173"
echo.

call npm run dev

:: ── After Vite exits (Ctrl+C or crash) ───────────────────────────────────────
popd 2>nul
goto CLEANUP

:: ═══════════════════════════════════════════════════════════════════════════════
::  SUBROUTINES
:: ═══════════════════════════════════════════════════════════════════════════════

:LOG_STEP
    echo.
    call :TIMESTAMP
    echo %C_CYN%[!TS!] [%~1] %~2%C_RST%
    echo %C_GRY%-------------------------------------------------------------%C_RST%
    goto :eof

:LOG_OK
    call :TIMESTAMP
    echo %C_GRN%[!TS!]   OK  %~1%C_RST%
    goto :eof

:LOG_INFO
    call :TIMESTAMP
    echo %C_YLW%[!TS!]   ..  %~1%C_RST%
    goto :eof

:LOG_ERR
    call :TIMESTAMP
    echo %C_RED%[!TS!]  ERR  %~1%C_RST%
    goto :eof

:TIMESTAMP
    for /f "tokens=1-3 delims=:." %%a in ("%TIME: =0%") do set "TS=%%a:%%b:%%c"
    goto :eof

:CHECK_PORT
    netstat -an 2>nul | findstr "LISTENING" | findstr ":%~1 " >nul 2>&1
    if !ERRORLEVEL! equ 0 (
        set "FATAL_MSG=Port %~1 is already in use. Required for: %~2"
        exit /b 1
    )
    exit /b 0

:FATAL_WITH_CLEANUP
    if "!COMPOSE_STARTED!"=="1" (
        echo.
        call :LOG_INFO "Shutting down Docker containers..."
        cd /d "%~dp0"
        docker compose down >nul 2>&1
        call :LOG_OK "Containers stopped."
    )

:FATAL
    echo.
    echo %C_RED%+----------------------------------------------------------+%C_RED%
    echo %C_RED%^|                      FATAL ERROR                         ^|%C_RED%
    echo %C_RED%+----------------------------------------------------------+%C_RED%
    call :TIMESTAMP
    echo %C_RED%[!TS!]  ERR  !FATAL_MSG!%C_RST%
    echo.
    pause
    exit /b 1

:CLEANUP
    if "!COMPOSE_STARTED!"=="1" (
        echo.
        call :LOG_INFO "Shutting down Docker containers..."
        cd /d "%~dp0"
        docker compose down >nul 2>&1
        call :LOG_OK "Containers stopped. Goodbye!"
    )
    echo.
    pause
    exit /b 0
