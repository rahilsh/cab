# Cab Marketplace Helm Chart

This chart deploys only the stateless API. PostgreSQL/PostGIS, Redis, OIDC, OSRM, object storage,
backup automation, and ingress certificates must be operated separately.

Create the referenced Secret through an external secret controller, sealed-secret workflow, or your
platform's secret manager. Do not commit the rendered Secret:

```text
cab-marketplace
  database-username
  database-password
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

Flyway runs at application startup. Keep `replicaCount: 1` for a migration-bearing release and wait
for readiness before enabling HPA or scaling out. See `docs/runbooks/migrations.md` for the required
pre-deployment checks and rollback constraints.
