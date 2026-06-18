# ==============================================================================
# setup-hmac-secret.ps1
# Idempotent HMAC secret bootstrap for Hotel PMS (Windows / PowerShell 5.1+)
# Run from the project root: .\setup-hmac-secret.ps1
# ==============================================================================

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

# ── Constants ──────────────────────────────────────────────────────────────────
$ROOT       = $PSScriptRoot
$ENV_FILE   = Join-Path $ROOT ".env"
$GITIGNORE  = Join-Path $ROOT ".gitignore"
$MARKER     = "INTERNAL_HMAC_SECRET"   # sentinel used for idempotency checks

# The YAML block to append.
# NOTE: the dollar sign in ${INTERNAL_HMAC_SECRET} must NOT be expanded by
# PowerShell, so the here-string uses single quotes (@' ... '@).
$YAML_BLOCK = @'


# Internal HMAC security - injected by setup-hmac-secret script
internal:
  hmac:
    secret: ${INTERNAL_HMAC_SECRET}
'@

$SERVICES = @(
    "api-gateway",
    "frontdesk-service",
    "billing-service",
    "guest-service",
    "fb-service"
)

# ── Helper ─────────────────────────────────────────────────────────────────────
function Write-Step([string]$msg) { Write-Host "`n[HMAC Setup] $msg" -ForegroundColor Cyan }
function Write-Ok([string]$msg)   { Write-Host "  OK  $msg" -ForegroundColor Green }
function Write-Skip([string]$msg) { Write-Host "  --  $msg" -ForegroundColor DarkGray }
function Write-Warn([string]$msg) { Write-Host "  !!  $msg" -ForegroundColor Yellow }

# ── Helper: generate cryptographically-random bytes ───────────────────────────
function New-RandomHex([int]$bytes) {
    $buf = [byte[]]::new($bytes)
    $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    $rng.GetBytes($buf)
    $rng.Dispose()
    return -join ($buf | ForEach-Object { $_.ToString("x2") })
}
function New-RandomBase64([int]$bytes) {
    $buf = [byte[]]::new($bytes)
    $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    $rng.GetBytes($buf)
    $rng.Dispose()
    return [Convert]::ToBase64String($buf)
}

# ── Step 1: Create/update .env with all required secrets ─────────────────────
Write-Step "Step 1 - .env file"

if (-Not (Test-Path $ENV_FILE)) {
    Set-Content -Path $ENV_FILE -Value "INTERNAL_HMAC_SECRET=$(New-RandomHex 32)" -Encoding UTF8
    Write-Ok ".env created with a fresh HMAC secret."
} else {
    Write-Skip ".env already exists - skipping HMAC secret generation to avoid accidental rotation."
}

# Append JWT_SECRET if not already present (idempotent)
$envContent = Get-Content $ENV_FILE -Raw -Encoding UTF8
if ($envContent -notmatch '(?m)^JWT_SECRET=') {
    Add-Content -Path $ENV_FILE -Value "JWT_SECRET=$(New-RandomBase64 48)" -Encoding UTF8
    Write-Ok "JWT_SECRET added to .env."
} else {
    Write-Skip "JWT_SECRET already in .env - skipping."
}

# Append POSTGRES_PASSWORD if not already present (idempotent)
$envContent = Get-Content $ENV_FILE -Raw -Encoding UTF8
if ($envContent -notmatch '(?m)^POSTGRES_PASSWORD=') {
    Add-Content -Path $ENV_FILE -Value "POSTGRES_PASSWORD=$(New-RandomHex 16)" -Encoding UTF8
    Write-Ok "POSTGRES_PASSWORD added to .env."
} else {
    Write-Skip "POSTGRES_PASSWORD already in .env - skipping."
}

# Append CONFIG_SERVER_PASSWORD if not already present (idempotent) — T-CFG-03
$envContent = Get-Content $ENV_FILE -Raw -Encoding UTF8
if ($envContent -notmatch '(?m)^CONFIG_SERVER_PASSWORD=') {
    Add-Content -Path $ENV_FILE -Value "CONFIG_SERVER_PASSWORD=$(New-RandomHex 24)" -Encoding UTF8
    Write-Ok "CONFIG_SERVER_PASSWORD added to .env."
} else {
    Write-Skip "CONFIG_SERVER_PASSWORD already in .env - skipping."
}

# ── Step 2: Ensure .env is in .gitignore ──────────────────────────────────────
Write-Step "Step 2 - .gitignore"

if (Test-Path $GITIGNORE) {
    $gitContent = Get-Content $GITIGNORE -Raw
    # Match .env as a standalone line (not part of a longer rule like .env.local)
    if ($gitContent -notmatch '(?m)^\s*\.env\s*$') {
        Add-Content -Path $GITIGNORE -Value "`r`n# Secrets`r`n.env" -Encoding UTF8
        Write-Ok ".env appended to .gitignore."
    } else {
        Write-Skip ".env is already listed in .gitignore."
    }
} else {
    Set-Content -Path $GITIGNORE -Value ".env" -Encoding UTF8
    Write-Ok ".gitignore created with .env entry."
}

# ── Steps 3+4: Append HMAC YAML block to all service application*.yml files ───
Write-Step "Steps 3+4 - Appending YAML blocks to all service application*.yml files"

foreach ($service in $SERVICES) {
    $resourcesDir = Join-Path $ROOT "$service\src\main\resources"

    if (-Not (Test-Path $resourcesDir)) {
        Write-Warn "$service - resources dir not found, skipping."
        continue
    }

    # Target application.yml AND application-<profile>.yml
    # Wrap in @() so that .Count is always defined even when 0 results are returned
    $yamlFiles = @(Get-ChildItem -Path $resourcesDir -Filter "application*.yml" -ErrorAction SilentlyContinue)

    if ($yamlFiles.Count -eq 0) {
        Write-Warn "$service - no application*.yml found, skipping."
        continue
    }

    foreach ($file in $yamlFiles) {
        $content = Get-Content $file.FullName -Raw -Encoding UTF8

        # Idempotency: skip if the sentinel is already present
        if ($content -match [regex]::Escape($MARKER)) {
            Write-Skip "$($file.Name) in $service already contains HMAC config."
            continue
        }

        # Ensure the file ends with a newline before appending
        if (-not $content.EndsWith("`n")) {
            Add-Content -Path $file.FullName -Value "" -Encoding UTF8
        }

        Add-Content -Path $file.FullName -Value $YAML_BLOCK -Encoding UTF8
        Write-Ok "$($file.Name) in $service - HMAC block appended."
    }
}

# ── Done ───────────────────────────────────────────────────────────────────────
Write-Host "`n[HMAC Setup] All done. Start the stack with:" -ForegroundColor Cyan
Write-Host "  docker compose --env-file .env up --build" -ForegroundColor White
