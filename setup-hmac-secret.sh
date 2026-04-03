#!/usr/bin/env bash
# ==============================================================================
# setup-hmac-secret.sh
# Idempotent HMAC secret bootstrap for Hotel PMS (Linux / macOS / Git Bash)
# Run from the project root: bash setup-hmac-secret.sh
# ==============================================================================

set -euo pipefail

# ── Constants ──────────────────────────────────────────────────────────────────
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ENV_FILE="$ROOT/.env"
GITIGNORE="$ROOT/.gitignore"
MARKER="INTERNAL_HMAC_SECRET"            # sentinel used for idempotency checks
SERVICES=(
    "api-gateway"
    "inventory-service"
    "reservation-service"
    "stay-service"
    "billing-service"
    "guest-service"
    "fb-service"
)

# The YAML block to append (leading blank line acts as separator)
read -r -d '' YAML_BLOCK << 'YAML_EOF' || true

# Internal HMAC security – injected by setup-hmac-secret script
internal:
  hmac:
    secret: ${INTERNAL_HMAC_SECRET}
YAML_EOF

# ── Colour helpers ─────────────────────────────────────────────────────────────
CYAN='\033[0;36m'; GREEN='\033[0;32m'; YELLOW='\033[0;33m'; GRAY='\033[0;37m'; NC='\033[0m'
step()  { echo -e "\n${CYAN}[HMAC Setup] $1${NC}"; }
ok()    { echo -e "  ${GREEN}✔  $1${NC}"; }
skip()  { echo -e "  ${GRAY}–  $1${NC}"; }
warn()  { echo -e "  ${YELLOW}⚠  $1${NC}"; }

# ── Step 1: Create .env with a cryptographically-random 64-char hex secret ────
step "Step 1 – .env file"

if [ ! -f "$ENV_FILE" ]; then
    # Generate 32 random bytes → 64 hex characters
    # Tries /dev/urandom first (Linux/macOS), falls back to openssl
    if command -v openssl &>/dev/null; then
        SECRET="$(openssl rand -hex 32)"
    else
        SECRET="$(xxd -l 32 -p /dev/urandom | tr -d '\n')"
    fi

    printf "INTERNAL_HMAC_SECRET=%s\n" "$SECRET" > "$ENV_FILE"
    ok ".env created with a fresh 64-char hex secret."
else
    skip ".env already exists – skipping secret generation to avoid rotation."
fi

# ── Step 2: Ensure .env is in .gitignore ──────────────────────────────────────
step "Step 2 – .gitignore"

if [ -f "$GITIGNORE" ]; then
    # grep -Fx matches a fixed string as a full line
    if ! grep -qFx ".env" "$GITIGNORE"; then
        printf "\n# Secrets\n.env\n" >> "$GITIGNORE"
        ok ".env appended to .gitignore."
    else
        skip ".env is already listed in .gitignore."
    fi
else
    printf ".env\n" > "$GITIGNORE"
    ok ".gitignore created with .env entry."
fi

# ── Step 3 & 4: Append HMAC YAML block to all service application*.yml files ──
step "Step 3 & 4 – Appending YAML blocks to all service application*.yml files"

for SERVICE in "${SERVICES[@]}"; do
    RESOURCES_DIR="$ROOT/$SERVICE/src/main/resources"

    if [ ! -d "$RESOURCES_DIR" ]; then
        warn "$SERVICE – resources dir not found, skipping."
        continue
    fi

    # Use nullglob-style check: collect matching files into an array
    YAML_FILES=()
    while IFS= read -r -d '' f; do
        YAML_FILES+=("$f")
    done < <(find "$RESOURCES_DIR" -maxdepth 1 -name "application*.yml" -print0 2>/dev/null)

    if [ ${#YAML_FILES[@]} -eq 0 ]; then
        warn "$SERVICE – no application*.yml found, skipping."
        continue
    fi

    for FILE in "${YAML_FILES[@]}"; do
        BASENAME="$(basename "$FILE")"

        # Idempotency check: skip if the sentinel string is already present
        if grep -q "$MARKER" "$FILE"; then
            skip "$BASENAME in $SERVICE already contains HMAC config."
            continue
        fi

        # Ensure file ends with a newline before appending
        if [ -s "$FILE" ] && [ "$(tail -c1 "$FILE" | wc -c)" -gt 0 ]; then
            # Last byte exists – check if it is a newline
            LAST_CHAR="$(tail -c1 "$FILE" | xxd -p 2>/dev/null || od -An -tx1 -N1 "$FILE" | tr -d ' ')"
            if [ "$LAST_CHAR" != "0a" ]; then
                echo "" >> "$FILE"
            fi
        fi

        printf "%s\n" "$YAML_BLOCK" >> "$FILE"
        ok "$BASENAME in $SERVICE – HMAC block appended."
    done
done

# ── Done ───────────────────────────────────────────────────────────────────────
echo -e "\n${CYAN}[HMAC Setup] All done. Start the stack with:${NC}"
echo -e "  docker compose --env-file .env up --build"
