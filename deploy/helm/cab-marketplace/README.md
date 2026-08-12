# Cab Marketplace Helm Chart

This chart deploys only the stateless API. PostgreSQL/PostGIS, Redis, OIDC, OSRM, object storage,
backup automation, and ingress certificates must be operated separately.

Create the referenced Secret through an external secret controller, sealed-secret workflow, or your
platform's secret manager. Do not commit the rendered Secret:

```text
cab-marketplace
  database-username
  database-password
  migration-database-username
  migration-database-password
  fake-payment-webhook-secret
```

Set `config.*` endpoints and install with the externally managed Secret and immutable image digest:

```bash
helm upgrade --install cab deploy/helm/cab-marketplace \
  --namespace cab --create-namespace \
  --set existingSecret.name=cab-marketplace \
  --set image.digest=sha256:0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef \
  --set config.databaseUrl=jdbc:postgresql://postgres.example:5432/cab \
  --set config.redisHost=redis.example \
  --set config.oidcIssuerUri=https://identity.example/realms/cab \
  --set config.osrmBaseUrl=http://osrm.routing:5000
```

When `image.digest` is set, the chart renders `repository@sha256:...` and ignores `image.tag`. The
pods run as UID/GID `10001`, drop all capabilities, use a read-only root filesystem, and mount a
bounded writable `/tmp`.

The application datasource must use a non-owner role without `BYPASSRLS`. By default, a Helm
pre-install/pre-upgrade Job runs the application image with the `migration` profile and separate
migration credentials, then exits. API pods keep Flyway disabled and never mount those credentials.
Set `migration.enabled=false` only when an external process has already applied the same migrations.
See `docs/runbooks/migrations.md`.

API pods mount the migration username, but never its password, so startup can reject equal runtime
and migration identities.

Tenant webhook secret references are restricted to `env:CAB_WEBHOOK_*`. Mount each referenced value
from an external Secret with `extraSecretEnv`; never put webhook values in a ConfigMap or values file.

NetworkPolicy is intentionally disabled by default because safe egress destinations are
environment-specific. Enable it only after supplying egress rules for PostgreSQL, Redis, OIDC, OSRM,
payment providers, and configured webhooks. Ingress is disabled by default; when enabled, TLS is
required unless `ingress.requireTls=false` is explicitly accepted for an internal environment.

Ride status SSE subscriptions are stored in-process. Keep ingress proxy buffering disabled for the
SSE path and provide external pub/sub before scaling replicas when cross-instance delivery is
required; the current registry only reaches clients connected to the publishing instance. Each
subscription begins with the current ride snapshot and accepts `Last-Event-ID`, but no historical
event replay or cross-replica fan-out is available.
