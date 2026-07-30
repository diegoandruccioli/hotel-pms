# Custom db-backup image — the stock postgres:15-alpine (used inline as the
# backup container's image before this) has pg_dumpall but no encryption or
# object-storage client. AUDIT_ANALISI_2026-07.md item 4: encrypt the backup
# and copy it off the single host it's created on.
FROM postgres:15-alpine

# age (https://github.com/FiloSottile/age): asymmetric encryption — this
# container only ever holds the public recipient key (AGE_RECIPIENT env var,
# not a secret), never the private key that decrypts. Compromising the
# backup host does not expose past or future backups.
RUN apk add --no-cache age bash

# mc (MinIO Client): talks to any S3-compatible endpoint (AWS S3, Backblaze
# B2, Wasabi, MinIO, ...) via `mc alias set`, not just MinIO despite the
# name. Pinned release + checksum verified at build time (ADR-002).
ARG MC_VERSION=RELEASE.2025-08-13T08-35-41Z
ARG MC_SHA256=01f866e9c5f9b87c2b09116fa5d7c06695b106242d829a8bb32990c00312e891
RUN wget -q -O /usr/local/bin/mc \
        "https://dl.min.io/client/mc/release/linux-amd64/archive/mc.${MC_VERSION}" \
    && echo "${MC_SHA256}  /usr/local/bin/mc" | sha256sum -c - \
    && chmod +x /usr/local/bin/mc

COPY backup.sh /usr/local/bin/backup.sh
RUN chmod +x /usr/local/bin/backup.sh

ENTRYPOINT ["/usr/local/bin/backup.sh"]
