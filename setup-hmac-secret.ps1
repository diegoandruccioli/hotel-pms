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
    "inventory-service",
    "reservation-service",
    "stay-service",
    "billing-service",
    "guest-service",
    "fb-service"
)

# ── Helper ─────────────────────────────────────────────────────────────────────
function Write-Step([string]$msg) { Write-Host "`n[HMAC Setup] $msg" -ForegroundColor Cyan }
function Write-Ok([string]$msg)   { Write-Host "  OK  $msg" -ForegroundColor Green }
function Write-Skip([string]$msg) { Write-Host "  --  $msg" -ForegroundColor DarkGray }
function Write-Warn([string]$msg) { Write-Host "  !!  $msg" -ForegroundColor Yellow }

# ── Step 1: Create .env with a cryptographically-random 64-char hex secret ────
Write-Step "Step 1 - .env file"

if (-Not (Test-Path $ENV_FILE)) {
    # Use .NET RandomNumberGenerator for a true 32-byte (64 hex char) secret
    $bytes  = [byte[]]::new(32)
    $rng    = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    $rng.GetBytes($bytes)
    $rng.Dispose()
    $secret = -join ($bytes | ForEach-Object { $_.ToString("x2") })

    Set-Content -Path $ENV_FILE -Value "INTERNAL_HMAC_SECRET=$secret" -Encoding UTF8
    Write-Ok ".env created with a fresh 64-char hex secret."
} else {
    Write-Skip ".env already exists - skipping secret generation to avoid accidental rotation."
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
