# PostgreSQL + pgBackRest — continuous WAL archiving + scheduled full/incremental
# backups, replacing the old db-backup sidecar (pg_dumpall on a fixed 24h cadence).
# archive_command runs as a subprocess of the Postgres engine itself, so pgBackRest
# has to live in this image, not a satellite container — see backup/DECISIONS.md §3.5.
FROM postgres:18-alpine

# Alpine's pgbackrest package depends on the generic "postgresql" meta-package
# (currently Alpine 3.24's postgresql18), which pulls in a second, unused
# Postgres server + client under /usr/ alongside this image's real v15 server
# under /usr/local/ (the official image builds from source, not from an apk
# package, so apk's resolver has no way to see it as already satisfied).
# Harmless, not a footgun: PATH is /usr/local/bin before /usr/bin, so psql/
# pg_dump/pg_isready/pgbackrest all still resolve to the v15 binaries —
# verified with `which` in the built image. Just disk bloat from a transitive
# dependency, not worth fighting apk's dependency resolution over.
RUN apk add --no-cache pgbackrest bash curl

COPY entrypoint-wrapper.sh /usr/local/bin/entrypoint-wrapper.sh
COPY backup-scheduler.sh /usr/local/bin/backup-scheduler.sh

RUN chmod +x /usr/local/bin/entrypoint-wrapper.sh /usr/local/bin/backup-scheduler.sh \
    && mkdir -p /var/lib/pgbackrest /var/log/pgbackrest /etc/pgbackrest \
    && chown -R postgres:postgres /var/lib/pgbackrest /var/log/pgbackrest /etc/pgbackrest

ENTRYPOINT ["entrypoint-wrapper.sh"]
CMD ["postgres"]
