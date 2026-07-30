#!/usr/bin/env bash
# db-backup entrypoint — pg_dumpall on an interval, gzip, optionally encrypt
# (age) and copy off-site (any S3-compatible endpoint via mc). Local-only
# behavior (no AGE_RECIPIENT / no S3_BUCKET set) is unchanged from before
# this file existed — both are opt-in.
set -uo pipefail

: "${BACKUP_RETENTION_DAYS:=14}"
: "${BACKUP_INTERVAL_SECONDS:=86400}"
: "${OFFSITE_RETENTION_DAYS:=30}"

log() {
    echo "[db-backup] $(date -Iseconds) $*"
}

if [[ -n "${S3_BUCKET:-}" ]]; then
    : "${S3_ENDPOINT:?S3_BUCKET is set but S3_ENDPOINT is missing}"
    : "${S3_ACCESS_KEY_ID:?S3_BUCKET is set but S3_ACCESS_KEY_ID is missing}"
    : "${S3_SECRET_ACCESS_KEY:?S3_BUCKET is set but S3_SECRET_ACCESS_KEY is missing}"
    mc alias set offsite "${S3_ENDPOINT}" "${S3_ACCESS_KEY_ID}" "${S3_SECRET_ACCESS_KEY}" \
        ${S3_REGION:+--api "S3v4"} >/dev/null
    OFFSITE_ENABLED=1
    log "off-site upload enabled -> ${S3_ENDPOINT}/${S3_BUCKET}"
else
    OFFSITE_ENABLED=0
    log "S3_BUCKET not set — off-site upload disabled, local backups only"
fi

if [[ -n "${AGE_RECIPIENT:-}" ]]; then
    ENCRYPT_ENABLED=1
    log "encryption enabled (age)"
else
    ENCRYPT_ENABLED=0
    log "AGE_RECIPIENT not set — backups stored unencrypted"
fi

log "starting — interval=${BACKUP_INTERVAL_SECONDS}s local_retention=${BACKUP_RETENTION_DAYS}d"

while true; do
    ts="$(date +%Y%m%d-%H%M%S)"
    sql_file="/backups/hotel-pms-${ts}.sql"

    if pg_dumpall -h postgres -U postgres -f "${sql_file}"; then
        gzip "${sql_file}"
        artifact="${sql_file}.gz"
        log "wrote ${artifact}"

        if [[ "${ENCRYPT_ENABLED}" -eq 1 ]]; then
            if age -r "${AGE_RECIPIENT}" -o "${artifact}.age" "${artifact}"; then
                rm -f "${artifact}"
                artifact="${artifact}.age"
                log "encrypted -> ${artifact}"
            else
                log "FAILED to encrypt ${artifact} — keeping unencrypted copy, skipping off-site upload this cycle"
                artifact=""
            fi
        fi

        if [[ "${OFFSITE_ENABLED}" -eq 1 && -n "${artifact}" ]]; then
            if mc cp "${artifact}" "offsite/${S3_BUCKET}/$(basename "${artifact}")"; then
                log "uploaded $(basename "${artifact}") to off-site storage"
            else
                log "FAILED to upload $(basename "${artifact}") to off-site storage"
            fi
        fi

        # Retention only runs after a successful dump — a failed dump cycle
        # must never delete backups that are still the most recent good copy.
        find /backups -type f \( -name '*.sql.gz' -o -name '*.sql.gz.age' \) \
            -mtime "+${BACKUP_RETENTION_DAYS}" -delete
        if [[ "${OFFSITE_ENABLED}" -eq 1 ]]; then
            mc rm --recursive --force --older-than "${OFFSITE_RETENTION_DAYS}d" \
                "offsite/${S3_BUCKET}/" >/dev/null 2>&1 || true
        fi
    else
        log "FAILED dump attempt — local and off-site retention pruning skipped this cycle"
    fi

    sleep "${BACKUP_INTERVAL_SECONDS}"
done
