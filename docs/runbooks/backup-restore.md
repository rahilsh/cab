# Backup And Restore Runbook

## Backup Policy

- PostgreSQL: use encrypted provider snapshots plus continuous WAL archiving for point-in-time
  recovery. Regularly capture a logical `pg_dump --format=custom` for portability.
- MinIO/S3: enable bucket versioning, object lock where policy requires it, and cross-region
  replication or scheduled `mc mirror` to a separate account.
- Keycloak: back up its production database and realm configuration. The repository realm import is
  local-only and is not a production identity backup.
- OSRM: retain the source PBF URL/checksum, profile, OSRM image digest, and preparation parameters;
  regenerate derived files instead of treating them as authoritative.
- Redis: no authoritative marketplace data lives in Redis. Preserve it only to reduce recovery time.

Encrypt backups with independently managed keys, restrict restore permissions, record retention,
and alert on missed jobs. Perform a documented restore drill at least quarterly.

The operator must define and approve service-specific recovery objectives. A starting example is
`RPO <= 5 minutes` for PostgreSQL and `RTO <= 60 minutes` for the API; replace these samples using
measured backup frequency, WAL lag, restore duration, data criticality, and business requirements.

## PostgreSQL Restore

1. Declare an incident, stop API writers, and select the recovery point with the business owner.
2. Restore into a new isolated PostgreSQL instance; install the matching PostGIS extension and create
   `cab_migration` and `cab_app` with operator-managed passwords. Make `cab_migration` the target
   database/schema owner; keep `cab_app` `NOSUPERUSER NOCREATEDB NOCREATEROLE NOBYPASSRLS`.

   ```sql
   CREATE ROLE cab_migration LOGIN PASSWORD '<migration-secret>'
     NOSUPERUSER NOCREATEDB NOCREATEROLE NOBYPASSRLS;
   CREATE ROLE cab_app LOGIN PASSWORD '<application-secret>'
     NOSUPERUSER NOCREATEDB NOCREATEROLE NOBYPASSRLS;
   ALTER DATABASE cab OWNER TO cab_migration;
   ALTER SCHEMA public OWNER TO cab_migration;
   ```

3. Run the logical restore as `cab_migration`: `pg_restore --clean --if-exists --no-owner --no-acl --dbname=<target> <backup>`.
4. Reapply application grants and default privileges from [the migration runbook](migrations.md);
   confirm tenant tables are not owned by `cab_app` and neither role has unexpected privileges.
5. Verify row counts, validated constraints, spatial indexes, `flyway_schema_history`, RLS policies,
   and balanced ledger data.
6. Start one API replica against the restored database and exercise health and synthetic workflows.
7. Switch traffic, monitor, then retain the old database read-only until the recovery is accepted.

Restore evidence objects to their original immutable object keys before reopening safety workflows.
After a credential-bearing backup restore, rotate database, OIDC, MinIO/S3, payment, and webhook
credentials as appropriate.
