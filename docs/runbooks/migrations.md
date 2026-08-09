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
`DATABASE_URL` unless `MIGRATION_DATABASE_URL` is supplied. The chart mounts migration credentials
only into its pre-install/pre-upgrade Job. API pods default to `FLYWAY_ENABLED=false` and receive
only application credentials.

## Before Deployment

1. Read every pending migration and test it against a recent production-sized restore.
2. Confirm it is backward compatible with the currently running application. Use expand/migrate/
   contract across releases for destructive changes.
3. Measure lock time and disk growth; schedule a maintenance window when the operation cannot remain
   online.
4. Take and verify a backup immediately before a risky migration.
5. Render the Helm migration Job with the exact image digest and production values, and confirm its deadline is sufficient.

## Apply And Verify

1. Run `helm upgrade`; watch the `*-migration` hook Job and preserve its logs.
2. Inspect `flyway_schema_history` and confirm exactly the expected versions succeeded.
3. Confirm the Job completed, wait for API readiness, and exercise critical reads/writes.

The hook passes `--app.migration=true` to select the dedicated `MigrationApplication` source and invokes the `migration` profile.
That minimal application disables the web server, Hibernate, scheduling, dispatch maintenance, and
outbox delivery; Spring Boot applies Flyway migrations and `MigrationRunner` then closes the context. Flyway's
PostgreSQL advisory locking prevents concurrent migrators, but it does not make unsafe DDL safe. For
long or incompatible migrations, use a separately reviewed Job with the API scaled to zero.

## Failure

- Stop the rollout and preserve logs and database state.
- If Flyway reports a failed transactional migration, diagnose and correct it in a new migration.
- Never run `flyway repair` against production without reviewing the schema and checksum impact.
- A code rollback is safe only when the new schema remains backward compatible. Otherwise restore
  the pre-migration backup according to [backup and restore](backup-restore.md).
