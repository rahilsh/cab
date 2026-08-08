# Database Migration Runbook

Flyway migrations are bundled in the application. PostgreSQL is authoritative; never use Hibernate
schema generation or edit an applied migration.

## Database Roles

Use distinct roles:

- The migration role owns the schema objects and can run DDL.
- The application role has `LOGIN`, `CONNECT`, schema `USAGE`, and table DML only. It must be
  `NOSUPERUSER`, `NOBYPASSRLS`, and must not own tenant tables.

Provision grants as the migration role before deploying migrations:

```sql
CREATE ROLE cab_app LOGIN PASSWORD '<secret>'
  NOSUPERUSER NOCREATEDB NOCREATEROLE NOBYPASSRLS;
GRANT CONNECT ON DATABASE cab TO cab_app;
GRANT USAGE ON SCHEMA public TO cab_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO cab_app;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO cab_app;
ALTER DEFAULT PRIVILEGES FOR ROLE cab_migration IN SCHEMA public
  GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO cab_app;
ALTER DEFAULT PRIVILEGES FOR ROLE cab_migration IN SCHEMA public
  GRANT USAGE, SELECT ON SEQUENCES TO cab_app;
```

Set `DATABASE_USERNAME`/`DATABASE_PASSWORD` to the application role. Set
`MIGRATION_DATABASE_USERNAME`/`MIGRATION_DATABASE_PASSWORD` to the migration role. Both use
`DATABASE_URL` unless `MIGRATION_DATABASE_URL` is supplied. Prefer running Flyway in a separately
controlled migration job and setting `FLYWAY_ENABLED=false` on application pods afterward. If
Flyway runs at application startup, protect the stronger migration credentials as deployment
credentials and keep the rollout at one replica until migration completes.

## Before Deployment

1. Read every pending migration and test it against a recent production-sized restore.
2. Confirm it is backward compatible with the currently running application. Use expand/migrate/
   contract across releases for destructive changes.
3. Measure lock time and disk growth; schedule a maintenance window when the operation cannot remain
   online.
4. Take and verify a backup immediately before a risky migration.
5. For startup migrations, set Helm `replicaCount: 1` and disable HPA for the migration-bearing rollout.

## Apply And Verify

1. Deploy one new pod and watch its logs for Flyway completion.
2. Inspect `flyway_schema_history` and confirm exactly the expected versions succeeded.
3. Wait for readiness, exercise critical reads/writes, then scale out.

Flyway's PostgreSQL advisory locking prevents concurrent application migrators, but it does not make
an unsafe DDL change safe. For long migrations, use a separately reviewed Flyway/SQL Job with the
application Deployment scaled to zero, then restore replicas. The application image does not expose
a migration-only command and must not be used as a Job that would remain running after startup.

## Failure

- Stop the rollout and preserve logs and database state.
- If Flyway reports a failed transactional migration, diagnose and correct it in a new migration.
- Never run `flyway repair` against production without reviewing the schema and checksum impact.
- A code rollback is safe only when the new schema remains backward compatible. Otherwise restore
  the pre-migration backup according to [backup and restore](backup-restore.md).
