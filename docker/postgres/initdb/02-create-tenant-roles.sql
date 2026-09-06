-- Per-service PostgreSQL roles — one login role per microservice database,
-- each granted rights only on its own database's public schema.
--
-- Before this file, every service connected as the `postgres` superuser
-- with the same password (docker-compose.yml SPRING_DATASOURCE_USERNAME):
-- a single compromised service had read/write on all 5 databases. This is
-- the mitigation chosen instead of Postgres Row Level Security (see
-- backup/DECISIONS.md ADR-004, which explicitly decided against RLS —
-- the isolation gap that actually existed was here, at the connection
-- level, not inside a single already-scoped schema).
--
-- Runs only on first container init (docker-entrypoint-initdb.d semantics —
-- an empty $PGDATA). An existing deployment must apply this by hand once
-- (see docs/OPERATIONS_RUNBOOK.md) since the initdb scripts never re-run
-- against a populated data directory.
--
-- \getenv (psql 10+) reads the password directly from the container's own
-- environment — never interpolated as SQL text, so a password containing
-- a quote can't break out of the CREATE ROLE statement.
\getenv auth_db_password AUTH_DB_PASSWORD
\getenv guest_db_password GUEST_DB_PASSWORD
\getenv frontdesk_db_password FRONTDESK_DB_PASSWORD
\getenv billing_db_password BILLING_DB_PASSWORD
\getenv fb_db_password FB_DB_PASSWORD

CREATE ROLE auth_service_app LOGIN PASSWORD :'auth_db_password';
CREATE ROLE guest_service_app LOGIN PASSWORD :'guest_db_password';
CREATE ROLE frontdesk_service_app LOGIN PASSWORD :'frontdesk_db_password';
CREATE ROLE billing_service_app LOGIN PASSWORD :'billing_db_password';
CREATE ROLE fb_service_app LOGIN PASSWORD :'fb_db_password';

GRANT ALL PRIVILEGES ON DATABASE hotel_auth TO auth_service_app;
GRANT ALL PRIVILEGES ON DATABASE hotel_guest TO guest_service_app;
GRANT ALL PRIVILEGES ON DATABASE hotel_frontdesk TO frontdesk_service_app;
GRANT ALL PRIVILEGES ON DATABASE hotel_billing TO billing_service_app;
GRANT ALL PRIVILEGES ON DATABASE hotel_fb TO fb_service_app;

-- PostgreSQL grants CONNECT on every database to PUBLIC by default (unlike
-- the public-SCHEMA default, this did NOT change in PG15) — without this,
-- every per-service role above could still open a connection to the other
-- 4 databases (verified: a role with zero grants elsewhere could run
-- `SELECT 1` against a database that isn't its own). The GRANT ALL above
-- already re-grants CONNECT to each database's own role explicitly.
REVOKE CONNECT ON DATABASE hotel_auth FROM PUBLIC;
REVOKE CONNECT ON DATABASE hotel_guest FROM PUBLIC;
REVOKE CONNECT ON DATABASE hotel_frontdesk FROM PUBLIC;
REVOKE CONNECT ON DATABASE hotel_billing FROM PUBLIC;
REVOKE CONNECT ON DATABASE hotel_fb FROM PUBLIC;

-- CREATE/USAGE on the "public" schema itself is separate from database-level
-- privileges — PostgreSQL 15 stopped granting CREATE on public to PUBLIC by
-- default, so each role needs it explicitly inside its own database before
-- Flyway can create the first table there.
\c hotel_auth
GRANT ALL ON SCHEMA public TO auth_service_app;

\c hotel_guest
GRANT ALL ON SCHEMA public TO guest_service_app;

\c hotel_frontdesk
GRANT ALL ON SCHEMA public TO frontdesk_service_app;

\c hotel_billing
GRANT ALL ON SCHEMA public TO billing_service_app;

\c hotel_fb
GRANT ALL ON SCHEMA public TO fb_service_app;
