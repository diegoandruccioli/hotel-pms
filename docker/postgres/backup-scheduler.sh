#!/usr/bin/env bash
# Scheduled full/incremental pgBackRest backups. WAL archiving itself (the
# part that actually drives RPO down to minutes) is NOT this script — it's
# archive_command, fired continuously by Postgres independent of this loop.
# This loop only takes periodic full/incremental base backups on top of that
# continuous WAL stream. No -e: a failed cycle must log + alert and retry
# next interval, never kill the loop.
set -uo pipefail

# Deliberately NOT prefixed PGBACKREST_ — pgbackrest auto-reads any
# PGBACKREST_* env var as one of its own options, and these three are our
# own orchestration knobs, not real pgbackrest options (logs a noisy but
# non-fatal WARN otherwise: "environment contains invalid option ...").
: "${BACKUP_STANZA:=hotel-pms}"
CONF=/etc/pgbackrest/pgbackrest.conf
: "${BACKUP_FULL_INTERVAL_SECONDS:=604800}"   # weekly
: "${BACKUP_INCR_INTERVAL_SECONDS:=86400}"    # daily — also the sleep between cycles

log() {
    echo "[backup-scheduler] $(date -Iseconds) $*"
}

alert() {
    local reason="$1"
    curl -fsS -X POST http://alertmanager:9093/api/v2/alerts \
        -H 'Content-Type: application/json' \
        -d "[{\"labels\":{\"alertname\":\"BackupCycleFailed\",\"severity\":\"critical\",\"job\":\"postgres-pgbackrest\",\"reason\":\"${reason}\"},\"annotations\":{\"summary\":\"pgBackRest ${reason} failed\",\"description\":\"stanza=${BACKUP_STANZA} — see hotel_postgres container logs\"}}]" \
        >/dev/null 2>&1 || log "WARN could not reach alertmanager to report ${reason} failure"
}

last_full=0

while true; do
    now=$(date +%s)
    if (( now - last_full >= BACKUP_FULL_INTERVAL_SECONDS )); then
        type=full
    else
        type=incr
    fi

    # pgbackrest's `backup` command targets exactly ONE repo per invocation
    # (defaults to repo1 if --repo is omitted) — unlike archive-push, which
    # pushes WAL to every configured repo automatically. Loop explicitly so
    # repo2 (off-site) actually gets its own backup set, not just WAL.
    repos=(1)
    [[ -n "${S3_BUCKET:-}" ]] && repos+=(2)

    cycle_ok=1
    for repo in "${repos[@]}"; do
        log "starting ${type} backup on repo${repo}"
        if pgbackrest --config="${CONF}" --stanza="${BACKUP_STANZA}" --repo="${repo}" --type="${type}" backup; then
            log "${type} backup succeeded on repo${repo}"
        else
            log "FAILED ${type} backup on repo${repo}"
            alert "backup-${type}-repo${repo}"
            cycle_ok=0
        fi
    done
    [[ "${type}" == "full" && "${cycle_ok}" -eq 1 ]] && last_full=${now}

    if pgbackrest --config="${CONF}" --stanza="${BACKUP_STANZA}" check; then
        log "check passed"
    else
        log "FAILED pgbackrest check after backup cycle"
        alert "check"
    fi

    sleep "${BACKUP_INCR_INTERVAL_SECONDS}"
done
