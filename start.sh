#!/usr/bin/env bash
# ═══════════════════════════════════════════════════════════════════════════════
#  Enterprise Hotel PMS – One-Click Startup (Linux / macOS)
#
#  Bulletproof: timestamps, colour-coded output, port pre-checks,
#  hard-fail Docker timeout, HMAC error capture, cleanup trap,
#  deferred browser launch (waits for Vite port to respond).
# ═══════════════════════════════════════════════════════════════════════════════

set -euo pipefail

# ── Resolve absolute script directory & lock working directory ────────────────
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# ── State ─────────────────────────────────────────────────────────────────────
COMPOSE_STARTED=0
BROWSER_PID=""

# ═══════════════════════════════════════════════════════════════════════════════
#  COLOUR PALETTE
# ═══════════════════════════════════════════════════════════════════════════════
BOLD='\033[1m'
RED='\033[0;91m'
GRN='\033[0;92m'
YLW='\033[0;93m'
CYN='\033[0;96m'
GRY='\033[0;90m'
NC='\033[0m'

# ═══════════════════════════════════════════════════════════════════════════════
#  LOGGING HELPERS
# ═══════════════════════════════════════════════════════════════════════════════
timestamp() { date '+%H:%M:%S'; }

log_step() {
    echo ""
    echo -e "${CYN}[$(timestamp)] [$1] $2${NC}"
    echo -e "${GRY}─────────────────────────────────────────────────────────────${NC}"
}

log_ok()   { echo -e "${GRN}[$(timestamp)]   ✅  $1${NC}"; }
log_info() { echo -e "${YLW}[$(timestamp)]   ℹ️   $1${NC}"; }
log_err()  { echo -e "${RED}[$(timestamp)]   ❌  $1${NC}"; }

show_banner() {
    echo ""
    echo -e "${CYN}╔══════════════════════════════════════════════════════════╗${NC}"
    echo -e "${CYN}║     🏨  Hotel PMS – One-Click Startup (Bash)            ║${NC}"
    echo -e "${CYN}╚══════════════════════════════════════════════════════════╝${NC}"
    echo ""
}

die() {
    echo ""
    echo -e "${RED}╔══════════════════════════════════════════════════════════╗${NC}"
    echo -e "${RED}║                    FATAL ERROR                          ║${NC}"
    echo -e "${RED}╚══════════════════════════════════════════════════════════╝${NC}"
    log_err "$1"
    echo ""
    echo -e "${YLW}Press Enter to close...${NC}"
    read -r
    exit 1
}

# ═══════════════════════════════════════════════════════════════════════════════
#  CLEANUP TRAP
# ═══════════════════════════════════════════════════════════════════════════════
cleanup() {
    local exit_code=$?

    # Kill background browser-waiter if still running
    if [[ -n "$BROWSER_PID" ]] && kill -0 "$BROWSER_PID" 2>/dev/null; then
        kill "$BROWSER_PID" 2>/dev/null || true
    fi

    if [[ "$COMPOSE_STARTED" -eq 1 ]]; then
        echo ""
        log_info "Shutting down Docker containers..."
        cd "$SCRIPT_DIR"
        docker compose down 2>/dev/null || true
        log_ok "Containers stopped. Goodbye!"
    fi

    echo ""
    if [[ $exit_code -ne 0 ]]; then
        echo -e "${YLW}Press Enter to close...${NC}"
        read -r
    fi
}
trap cleanup EXIT INT TERM

# ═══════════════════════════════════════════════════════════════════════════════
#  PORT PRE-CHECK
# ═══════════════════════════════════════════════════════════════════════════════
assert_port_free() {
    local port="$1"
    local label="$2"
    local in_use=0

    if command -v ss &>/dev/null; then
        ss -tln 2>/dev/null | grep -q ":${port} " && in_use=1
    elif command -v lsof &>/dev/null; then
        lsof -iTCP:"$port" -sTCP:LISTEN &>/dev/null && in_use=1
    elif command -v netstat &>/dev/null; then
        netstat -tln 2>/dev/null | grep -q ":${port} " && in_use=1
    fi

    if [[ "$in_use" -eq 1 ]]; then
        die "Port $port is already in use. Required for: $label"
    fi
}

# ═══════════════════════════════════════════════════════════════════════════════
#  DOCKER DAEMON DETECTION & LAUNCH
# ═══════════════════════════════════════════════════════════════════════════════
start_docker() {
    log_info "Checking if Docker daemon is active..."

    if docker ps >/dev/null 2>&1; then
        log_ok "Docker is already running!"
        return 0
    fi

    log_info "Docker daemon is not active. Attempting to start..."

    # ── Try cached path ───────────────────────────────────────────────────────
    local cache_dir="${HOME}/.cache/HotelPMS"
    local cache_file="${cache_dir}/docker-path"
    local docker_exe=""

    if [[ -f "$cache_file" ]]; then
        docker_exe="$(cat "$cache_file" 2>/dev/null)"
        if [[ -n "$docker_exe" && -x "$docker_exe" ]]; then
            log_info "Cached Docker path: $docker_exe"
        else
            docker_exe=""
        fi
    fi

    # ── OS-specific detection ─────────────────────────────────────────────────
    if [[ -z "$docker_exe" ]]; then
        if [[ "$OSTYPE" == darwin* ]]; then
            # macOS: Docker Desktop
            local mac_paths=(
                "/Applications/Docker.app/Contents/MacOS/Docker Desktop"
                "/Applications/Docker.app/Contents/MacOS/Docker"
                "${HOME}/Applications/Docker.app/Contents/MacOS/Docker Desktop"
                "${HOME}/Applications/Docker.app/Contents/MacOS/Docker"
            )
            for p in "${mac_paths[@]}"; do
                if [[ -x "$p" ]]; then
                    docker_exe="$p"
                    break
                fi
            done

            if [[ -z "$docker_exe" && -d "/Applications/Docker.app" ]]; then
                # Fallback: use open -a
                log_info "Starting Docker Desktop via 'open -a Docker'..."
                open -a Docker 2>/dev/null || true
                docker_exe="__launched_via_open__"
            fi
        else
            # Linux: systemctl / service
            log_info "Attempting to start Docker daemon via systemd/service..."
            if command -v systemctl &>/dev/null; then
                sudo systemctl start docker 2>/dev/null || true
            elif command -v service &>/dev/null; then
                sudo service docker start 2>/dev/null || true
            fi
            docker_exe="__launched_via_systemctl__"
        fi
    fi

    if [[ -z "$docker_exe" ]]; then
        die "Docker Desktop/Engine not found. Install from https://www.docker.com/products/docker-desktop"
    fi

    # Launch if we have a direct executable (not __launched_via_*__)
    if [[ "$docker_exe" != __launched_via_* ]]; then
        log_info "Starting Docker from: $docker_exe"
        nohup "$docker_exe" >/dev/null 2>&1 &
        # Cache for next run
        mkdir -p "$cache_dir"
        echo "$docker_exe" > "$cache_file"
    fi

    # ── Poll for daemon readiness ─────────────────────────────────────────────
    local max_wait=90
    local poll=3
    local elapsed=0

    log_info "Waiting for Docker daemon (timeout ${max_wait}s)..."
    while [[ "$elapsed" -lt "$max_wait" ]]; do
        if docker ps >/dev/null 2>&1; then
            echo ""
            log_ok "Docker daemon is now active!"
            return 0
        fi
        printf '.' >&2
        sleep "$poll"
        elapsed=$((elapsed + poll))
    done
    echo ""
    die "Docker daemon did not respond within ${max_wait}s. Start Docker manually and retry."
}

# ═══════════════════════════════════════════════════════════════════════════════
#  HTTP HEALTH CHECK
# ═══════════════════════════════════════════════════════════════════════════════
wait_for_healthy() {
    local name="$1"
    local url="$2"
    local timeout="${3:-120}"
    local elapsed=0

    log_info "Polling $name at $url (timeout ${timeout}s)..."
    while [[ "$elapsed" -lt "$timeout" ]]; do
        if curl -sf "$url" >/dev/null 2>&1; then
            echo ""
            log_ok "$name is healthy!"
            return 0
        fi
        printf '.' >&2
        sleep 3
        elapsed=$((elapsed + 3))
    done
    echo ""
    die "$name did not become healthy within ${timeout}s. Check logs: docker compose logs $(echo "$name" | tr '[:upper:]' '[:lower:]' | tr ' ' '-')"
}

# ═══════════════════════════════════════════════════════════════════════════════
#  BACKGROUND BROWSER LAUNCHER
# ═══════════════════════════════════════════════════════════════════════════════
launch_browser_when_ready() {
    local max_wait=30
    local waited=0
    while [[ "$waited" -lt "$max_wait" ]]; do
        if curl -sf http://localhost:5173 >/dev/null 2>&1; then
            if command -v xdg-open &>/dev/null; then
                xdg-open http://localhost:5173 2>/dev/null &
            elif command -v open &>/dev/null; then
                open http://localhost:5173 2>/dev/null &
            fi
            return 0
        fi
        sleep 1
        waited=$((waited + 1))
    done
}

# ═══════════════════════════════════════════════════════════════════════════════
#  MAIN EXECUTION
# ═══════════════════════════════════════════════════════════════════════════════

show_banner

# ── Step 0: Port pre-checks ──────────────────────────────────────────────────
log_step "0/7" "Pre-flight port checks"
assert_port_free 8888 "Config Server"
assert_port_free 8080 "API Gateway"
assert_port_free 5173 "Vite Dev Server"
assert_port_free 5432 "PostgreSQL"
assert_port_free 6379 "Redis"
log_ok "All required ports are available."

# ── Step 1: Docker ────────────────────────────────────────────────────────────
log_step "1/7" "Ensuring Docker is running"
start_docker

# ── Step 2: HMAC Secret ──────────────────────────────────────────────────────
log_step "2/7" "HMAC secret bootstrap"
hmac_script="$SCRIPT_DIR/setup-hmac-secret.sh"
if [[ ! -f "$hmac_script" ]]; then
    die "HMAC setup script not found at: $hmac_script"
fi
if ! bash "$hmac_script"; then
    die "HMAC secret setup failed. Check the output above for details."
fi
log_ok "HMAC secret is ready."

# ── Step 3: Docker Compose ────────────────────────────────────────────────────
log_step "3/7" "Starting Docker infrastructure"
if [[ ! -f "$SCRIPT_DIR/.env" ]]; then
    die ".env file not found — HMAC setup may have failed silently."
fi
docker compose --env-file .env up -d --build
COMPOSE_STARTED=1
log_ok "All containers are starting."

# ── Step 4: Config Server health ──────────────────────────────────────────────
log_step "4/7" "Waiting for Config Server"
wait_for_healthy "Config Server" "http://localhost:8888/actuator/health/liveness" 120

# ── Step 5: API Gateway health ────────────────────────────────────────────────
log_step "5/7" "Waiting for API Gateway"
wait_for_healthy "API Gateway" "http://localhost:8080/actuator/health/liveness" 180

# ── Step 6: Frontend dependencies ─────────────────────────────────────────────
log_step "6/7" "Installing frontend dependencies"
frontend_dir="$SCRIPT_DIR/frontend"
if [[ ! -d "$frontend_dir" ]]; then
    die "frontend/ directory not found at: $frontend_dir"
fi
cd "$frontend_dir"
npm install --silent
log_ok "Frontend dependencies installed."

# ── Step 7: Launch Vite + browser ─────────────────────────────────────────────
log_step "7/7" "Launching Vite dev server"

# Start background browser opener (waits for port 3000)
launch_browser_when_ready &
BROWSER_PID=$!

log_info "Press Ctrl+C to stop the server and shut down Docker."
log_ok "Vite dev server starting on http://localhost:5173"
echo ""

npm run dev
