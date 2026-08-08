# Production Roadmap

The roadmap is delivered through independently green Conventional Commits.

## Foundation

- Reproducible build, unit coverage above 85%, and actual HTTP integration tests
- Apache-2.0 project and community policies
- PostgreSQL/PostGIS, Flyway, Redis, safe configuration, and health endpoints

## Security And Tenancy

- OIDC resource server and Keycloak development realm
- `X-Tenant-ID` selection validated against database membership
- Tenant-qualified constraints, queries, cache keys, jobs, audit events, and isolation tests

## Marketplace

- Service areas and OSRM routing
- Riders, drivers, compliance documents, vehicles, shifts, and availability
- Products, pricing rules, quotes, rides, dispatch offers, and complete trip lifecycle
- Provider-neutral payments, refunds, notifications, ledger, and settlements
- Ratings, support, safety, audit, and signed outbound webhooks

## Operations

- OpenAPI, metrics, tracing, structured logs, rate limits, and runbooks
- Docker Compose stack and production container
- Helm chart, SBOM, vulnerability scanning, signed releases, and backup restoration tests

Production readiness requires concurrency, cross-tenant isolation, migration, load, recovery, and
container smoke tests to pass in CI.
