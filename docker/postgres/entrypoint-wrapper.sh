#!/usr/bin/env bash
# Wraps the official postgres:15-alpine entrypoint: starts Postgres with WAL
# archiving turned on (continuous archive_command via pgBackRest, RPO measured
# in minutes via archive_timeout — not the old 24h pg_dumpall cadence), waits
# for readiness, creates the pgBackRest stanza if needed, then launches the
# scheduled full/incremental backup loop in the background. Our own PID stays
# PID 1 so `docker stop` reaches Postgres for a normal graceful shutdown.
set -Eeo pipefail

STANZA="${PGBACKREST_STANZA:-hotel-pms}"
CONF=/etc/pgbackrest/pgbackrest.conf
ARCHIVE_TIMEOUT="${PGBACKREST_ARCHIVE_TIMEOUT:-120}"

log() {
    echo "[postgres-entrypoint] $(date -Iseconds) $*"
}

alert() {
    local reason="$1"
    curl -fsS -X POST http://alertmanager:9093/api/v2/alerts \
        -H 'Content-Type: application/json' \
        -d "[{\"labels\":{\"alertname\":\"BackupCycleFailed\",\"severity\":\"critical\",\"job\":\"postgres-pgbackrest\",\"reason\":\"${reason}\"},\"annotations\":{\"summary\":\"pgBackRest ${reason} failed\",\"description\":\"stanza=${STANZA} — see hotel_postgres container logs\"}}]" \
        >/dev/null 2>&1 || log "WARN could not reach alertmanager to report ${reason} failure"
}

write_pgbackrest_conf() {
    : "${PGBACKREST_REPO1_RETENTION_FULL:=4}"

    {
        echo "[global]"
        echo "repo1-path=/var/lib/pgbackrest/repo1"
        echo "repo1-retention-full=${PGBACKREST_REPO1_RETENTION_FULL}"
        if [[ -n "${PGBACKREST_CIPHER_PASS:-}" ]]; then
            echo "repo1-cipher-type=aes-256-cbc"
            echo "repo1-cipher-pass=${PGBACKREST_CIPHER_PASS}"
        fi

        if [[ -n "${S3_BUCKET:-}" ]]; then
            : "${PGBACKREST_REPO2_RETENTION_FULL:=8}"
            echo "repo2-type=s3"
            echo "repo2-s3-endpoint=${S3_ENDPOINT#https://}"
            echo "repo2-s3-bucket=${S3_BUCKET}"
            echo "repo2-s3-key=${S3_ACCESS_KEY_ID}"
            echo "repo2-s3-key-secret=${S3_SECRET_ACCESS_KEY}"
            echo "repo2-s3-region=${S3_REGION:-us-east-1}"
            echo "repo2-path=/hotel-pms"
            echo "repo2-retention-full=${PGBACKREST_REPO2_RETENTION_FULL}"
            # Bundles small files into fewer, larger objects before upload —
            # a Postgres data dir is thousands of small files, and without
            # this each one is its own S3 PUT request (round-trip latency
            # dominates, not bandwidth: a small dev backup took minutes).
            echo "repo2-bundle=y"
            echo "repo2-block=y"
            if [[ -n "${PGBACKREST_CIPHER_PASS:-}" ]]; then
                echo "repo2-cipher-type=aes-256-cbc"
                echo "repo2-cipher-pass=${PGBACKREST_CIPHER_PASS}"
            fi
        fi

        echo "process-max=2"
        echo "log-level-console=info"
        echo "log-level-file=detail"
        echo "log-path=/var/log/pgbackrest"
        echo ""
        echo "[${STANZA}]"
        echo "pg1-path=/var/lib/postgresql/data"
        echo "pg1-socket-path=/var/run/postgresql"
    } > "${CONF}"
    chmod 600 "${CONF}"
    chown postgres:postgres "${CONF}"

    if [[ -z "${PGBACKREST_CIPHER_PASS:-}" ]]; then
        log "WARN PGBACKREST_CIPHER_PASS not set — repo1/repo2 stored UNENCRYPTED. Generate one with: openssl rand -base64 48"
    fi
    if [[ -z "${S3_BUCKET:-}" ]]; then
        log "S3_BUCKET not set — off-site repo2 disabled, local repo1 only"
    fi
}

write_pgbackrest_conf

# Start Postgres in the background; our own shell stays PID 1.
docker-entrypoint.sh postgres \
    -c archive_mode=on \
    -c "archive_command=pgbackrest --config=${CONF} --stanza=${STANZA} archive-push %p" \
    -c archive_timeout="${ARCHIVE_TIMEOUT}" \
    -c wal_level=replica &
PG_PID=$!

SHUTTING_DOWN=0
shutdown() {
    SHUTTING_DOWN=1
    log "shutting down — forwarding signal to postgres (pid ${PG_PID})"
    kill -TERM "${PG_PID}" 2>/dev/null || true
    [[ -n "${SUPERVISOR_PID:-}" ]] && kill -TERM "${SUPERVISOR_PID}" 2>/dev/null || true
    wait "${PG_PID}" 2>/dev/null || true
    exit 0
}
trap shutdown TERM INT

log "waiting for postgres to accept connections..."
# On a genuinely fresh volume, docker-entrypoint.sh starts a TEMPORARY
# server to run /docker-entrypoint-initdb.d/* (our 3 role/database SQL
# scripts), then stops it and starts the real one — pg_isready succeeding
# once can be that temporary instance. Require 3 consecutive successes
# (1s apart) before proceeding: the stop/start dip in between resets the
# counter, so this waits out the whole restart instead of racing it.
# Verified with a real fresh-volume run: without this, the very first
# backup cycle below started against the temp server and was aborted mid-
# flight by its shutdown ("NULL result required to complete request").
consecutive_ready=0
until [[ "${consecutive_ready}" -ge 3 ]]; do
    if gosu postgres pg_isready -h /var/run/postgresql -U postgres >/dev/null 2>&1; then
        consecutive_ready=$((consecutive_ready + 1))
    else
        consecutive_ready=0
    fi
    sleep 1
done
log "postgres is ready"

if gosu postgres pgbackrest --config="${CONF}" --stanza="${STANZA}" stanza-create 2>>/var/log/pgbackrest/stanza-create.log; then
    log "pgbackrest stanza '${STANZA}' ready"
else
    log "FAILED pgbackrest stanza-create — WAL archiving will fail until this is fixed"
    alert "stanza-create"
fi

# Textfile-collector dir for backup-scheduler.sh's heartbeat/last-success
# metrics (docker-compose.yml pgbackrest_metrics volume, node-exporter reads
# it read-only). Created here, not in the Dockerfile, since it's a volume
# mount point — anything baked into the image at that path would be hidden
# by the mount anyway.
mkdir -p /var/lib/node_exporter/textfile_collector
chown postgres:postgres /var/lib/node_exporter/textfile_collector

# NOTE: deliberately NOT prefixed PGBACKREST_ — pgbackrest itself auto-reads
# any PGBACKREST_* env var as one of its own options; STANZA/CONF picked that
# prefix up as (invalid) options "conf" etc. and logged noisy WARNs. These
# two are our own orchestration knobs, not pgbackrest options.
#
# Supervised, not a bare background job: backup-scheduler.sh's own `while
# true` loop should never exit on its own, so if it ever does (crash, OOM,
# an unset-variable error under `set -u`), that WAS silent before — this
# wrapper only ever `wait`ed on Postgres's PID, so the container stayed
# "healthy" with zero backups actually running until someone noticed by hand.
supervise_scheduler() {
    while true; do
        # `|| EXIT_CODE=$?` (not a bare statement) is required here: this
        # script runs under `set -e`, and a background job inherits it —
        # a plain nonzero-exit statement would kill this whole function
        # immediately on the first crash, before any of the restart/alert
        # logic below ever ran.
        EXIT_CODE=0
        BACKUP_STANZA="${STANZA}" gosu postgres backup-scheduler.sh || EXIT_CODE=$?
        [[ "${SHUTTING_DOWN}" -eq 1 ]] && return 0
        log "FATAL backup-scheduler.sh exited unexpectedly (code ${EXIT_CODE}) — restarting in 10s"
        alert "scheduler-crashed"
        sleep 10
    done
}
supervise_scheduler &
SUPERVISOR_PID=$!

wait "${PG_PID}"
