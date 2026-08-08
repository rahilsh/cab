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

## PostgreSQL Restore

1. Declare an incident, stop API writers, and select the recovery point with the business owner.
2. Restore into a new isolated PostgreSQL instance; install the matching PostGIS extension.
3. For logical backups, use `pg_restore --clean --if-exists --no-owner --dbname=<target> <backup>`.
4. Verify row counts, constraints, spatial indexes, `flyway_schema_history`, and balanced ledger data.
5. Start one API replica against the restored database and exercise health and synthetic workflows.
6. Switch traffic, monitor, then retain the old database read-only until the recovery is accepted.

Restore evidence objects to their original immutable object keys before reopening safety workflows.
After a credential-bearing backup restore, rotate database, OIDC, MinIO/S3, payment, and webhook
credentials as appropriate.
