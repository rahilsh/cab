#!/bin/bash
set -Eeuo pipefail

: "${APP_DATABASE_USERNAME:?APP_DATABASE_USERNAME is required}"
: "${APP_DATABASE_PASSWORD:?APP_DATABASE_PASSWORD is required}"

for attempt in {1..30}; do
  if pg_isready --host "${PGHOST:-postgres}" --username "$POSTGRES_USER" --dbname "$POSTGRES_DB"; then
    break
  fi
  if [[ "$attempt" -eq 30 ]]; then
    printf '%s\n' 'PostgreSQL did not become ready for application role bootstrap.' >&2
    exit 1
  fi
  sleep 1
done

psql --set ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" \
  --set app_username="$APP_DATABASE_USERNAME" --set app_password="$APP_DATABASE_PASSWORD" <<'SQL'
SELECT format(
    'CREATE ROLE %I LOGIN PASSWORD %L NOSUPERUSER NOCREATEDB NOCREATEROLE NOBYPASSRLS',
    :'app_username', :'app_password')
WHERE NOT EXISTS (SELECT FROM pg_roles WHERE rolname = :'app_username') \gexec

SELECT format(
    'ALTER ROLE %I PASSWORD %L NOSUPERUSER NOCREATEDB NOCREATEROLE NOBYPASSRLS',
    :'app_username', :'app_password') \gexec
SELECT format('GRANT CONNECT ON DATABASE %I TO %I', current_database(), :'app_username') \gexec
SELECT format('GRANT USAGE ON SCHEMA public TO %I', :'app_username') \gexec
SELECT format(
    'GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO %I',
    :'app_username') \gexec
SELECT format(
    'GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO %I',
    :'app_username') \gexec
SELECT format(
    'ALTER DEFAULT PRIVILEGES FOR ROLE %I IN SCHEMA public '
    'GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO %I',
    current_user, :'app_username') \gexec
SELECT format(
    'ALTER DEFAULT PRIVILEGES FOR ROLE %I IN SCHEMA public '
    'GRANT USAGE, SELECT ON SEQUENCES TO %I',
    current_user, :'app_username') \gexec
SQL
