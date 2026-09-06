-- Read-only monitoring role for postgres-exporter (docker-compose.yml).
-- Uses the built-in pg_monitor role (PostgreSQL 10+): grants SELECT on the
-- pg_stat_* views and EXECUTE on monitoring functions, without superuser
-- and without access to any table data in the 5 application databases.
\getenv postgres_exporter_password POSTGRES_EXPORTER_PASSWORD

CREATE ROLE postgres_exporter LOGIN PASSWORD :'postgres_exporter_password';
GRANT pg_monitor TO postgres_exporter;
