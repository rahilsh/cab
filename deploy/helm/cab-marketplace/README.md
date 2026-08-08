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

Set `config.*` endpoints and install with an immutable image tag:

```bash
helm upgrade --install cab deploy/helm/cab-marketplace \
  --namespace cab --create-namespace \
  --set image.tag=1.0.0 \
  --set config.databaseUrl=jdbc:postgresql://postgres.example:5432/cab \
  --set config.redisHost=redis.example \
  --set config.oidcIssuerUri=https://identity.example/realms/cab \
  --set config.osrmBaseUrl=http://osrm.routing:5000
```

The pod runs as UID/GID `10001`, drops all capabilities, uses a read-only root filesystem, and
mounts a bounded writable `/tmp`. Enable `networkPolicy` only after supplying environment-specific
egress rules for PostgreSQL, Redis, OIDC, OSRM, payment providers, and configured webhooks.

The application datasource must use a non-owner role without `BYPASSRLS`. Flyway uses the separate
migration credentials. Prefer an external migration job and set `FLYWAY_ENABLED=false` on the
Deployment with `config.flywayEnabled=false` when your platform supports it; migration credential
keys are then not mounted. Otherwise keep `replicaCount: 1` for a migration-bearing release and wait
for readiness before scaling out. See `docs/runbooks/migrations.md`.

Ride status SSE subscriptions are stored in-process. Keep ingress proxy buffering disabled for the
SSE path and provide external pub/sub before scaling replicas when cross-instance delivery is
required; the current registry only reaches clients connected to the publishing instance.
