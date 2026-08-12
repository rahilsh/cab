# Cab Marketplace

Cab Marketplace is an open-source backend for operating multi-tenant ride-hailing services. The
project is evolving from a single-fleet prototype into a production-oriented modular monolith for
operators, riders, drivers, vehicles, pricing, dispatch, trips, payments, and settlements.

> [!WARNING]
> The current code is an experimental prototype. Authentication and initial tenant isolation are
> present, but production hardening is incomplete. Do not expose it publicly or use it for real
> bookings.

The customer and operator frontends will be maintained in separate repositories. This repository
contains the HTTP API and backend services only.

## Project Status

The current implementation supports:

- City and cab registration
- Basic cab availability states
- Booking creation with preliminary idempotency support
- Redis GEO helpers
- Distance- and idle-time-based selection policies
- Paginated cab and booking queries
- Unit tests and random-port HTTP integration tests
- PostgreSQL/PostGIS persistence managed by Flyway
- RFC 9457 validation errors, correlation IDs, and health probes
- OIDC-protected tenant provisioning and operator memberships
- Tenant-owned PostGIS service areas
- OSRM-backed route distance and duration estimates
- Tenant-scoped rider and driver profiles, driver approval, vehicles, and driver shifts
- Tenant-scoped service products, versioned pricing rules, and immutable rider fare quotes

The production roadmap includes:

- Strict tenant isolation and broader role-based access
- Driver document upload and verification workflows
- Dispatch offers and complete trip lifecycle
- Provider-neutral payments, notifications, refunds, and settlements
- Auditing, webhooks, ratings, support, and safety workflows
- OpenAPI, observability, Docker Compose, and a Helm chart

See [the production roadmap](docs/ROADMAP.md) for sequencing and acceptance criteria.

## Architecture

The target architecture is a package-modular Spring Boot monolith:

```text
HTTP API
   |
OIDC + tenant authorization
   |
application modules
   |-- tenancy and access
   |-- geography and pricing
   |-- riders, drivers, and fleet
   |-- rides and dispatch
   |-- payments and settlements
   `-- notifications, support, safety, and webhooks
   |
PostgreSQL/PostGIS ------ transactional outbox
   |
Redis location index     OSRM routing
```

PostgreSQL is authoritative. Redis stores ephemeral live-location and dispatch indexes. External
side effects are delivered through a transactional outbox. See [ADR 0001](docs/adr/0001-modular-monolith.md)
and [ADR 0002](docs/adr/0002-shared-schema-tenancy.md).

## Requirements

- Java 21
- Git
- Docker with Compose for the production-oriented local stack as it is introduced
- PostgreSQL 17 with PostGIS 3.5
- Redis 7

Maven does not need to be installed because the repository includes a pinned Maven Wrapper.

## Build And Test

```bash
./mvnw clean verify
```

When using Colima on macOS, expose its socket to Testcontainers:

```bash
colima start --cpu 8 --memory 16
DOCKER_HOST="unix://${HOME}/.colima/default/docker.sock" \
TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock \
./mvnw clean verify
```

This command:

- Compiles and packages an executable Spring Boot JAR
- Runs unit tests
- Enforces at least 85% aggregate unit-test line coverage
- Starts the application on a random real port for HTTP integration tests

Coverage is written to `target/site/jacoco/index.html`. Integration tests use the `*IT` suffix and
are run by Maven Failsafe. MockMvc tests do not qualify as API integration tests.

Create a local database before running the current prototype:

```bash
docker run --name cab-postgis --rm \
  -e POSTGRES_DB=cab \
  -e POSTGRES_USER=cab \
  -e POSTGRES_PASSWORD=cab \
  -p 5432:5432 \
  postgis/postgis:17-3.5
```

In another terminal:

```bash
./mvnw spring-boot:run
```

The application reads `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD`, `REDIS_HOST`,
`REDIS_PORT`, and `OSRM_BASE_URL`. OSRM defaults to `http://localhost:5000`. Defaults are intended
for local development only. Flyway applies pending migrations; Hibernate validates the resulting
schema and never creates or drops production tables.

## API

The legacy prototype endpoints are blocked by the security policy and will be removed. The first
versioned endpoint is:

| Method | Path | Purpose |
| --- | --- | --- |
| `POST` | `/api/v1/tenants` | Provision a tenant; requires `platform.admin` scope |
| `GET` | `/api/v1/tenants` | List the authenticated account's tenant memberships |
| `GET` | `/api/v1/current-tenant` | Inspect the selected tenant context |
| `POST` | `/api/v1/service-areas` | Create a tenant service area; requires `TENANT_ADMIN` |
| `GET` | `/api/v1/service-areas` | List the selected tenant's service areas |
| `POST` | `/api/v1/routes/estimate` | Estimate driving distance and duration through OSRM |
| `POST`, `GET`, `PUT` | `/api/v1/rider/profile` | Create, read, or update the authenticated rider profile |
| `POST`, `GET` | `/api/v1/drivers` | Onboard or list drivers; requires `TENANT_ADMIN` |
| `POST` | `/api/v1/drivers/{id}/approve` | Approve a pending driver; requires `TENANT_ADMIN` |
| `GET`, `PUT` | `/api/v1/drivers/me` | Read or update the authenticated driver profile |
| `POST`, `GET` | `/api/v1/vehicles` | Create or list vehicles; requires `TENANT_ADMIN` |
| `PUT` | `/api/v1/vehicles/{id}` | Versioned vehicle update; requires `TENANT_ADMIN` |
| `POST`, `GET` | `/api/v1/driver/shifts` | Create or list the authenticated driver's shifts |
| `POST` | `/api/v1/driver/shifts/{id}/go-online` | Move an `OFFLINE` shift to `AVAILABLE` |
| `POST` | `/api/v1/driver/shifts/{id}/go-offline` | Move an `AVAILABLE` shift to `OFFLINE` |
| `POST` | `/api/v1/current-tenant/roles/{role}` | Admin self-grant of `RIDER` or `DRIVER` for operations/testing |
| `POST`, `GET` | `/api/v1/products` | Create or list service products; requires `TENANT_ADMIN` |
| `POST`, `GET` | `/api/v1/pricing-rules` | Create or list versioned pricing rules; requires `TENANT_ADMIN` |
| `POST`, `GET` | `/api/v1/quotes` | Create or list the authenticated rider's immutable fare quotes |
| `GET` | `/api/v1/quotes/{id}` | Read one fare quote owned by the authenticated rider |

Operational probes are available at `/actuator/health/liveness` and
`/actuator/health/readiness`. API responses include `X-Correlation-ID`; clients may supply this
header to correlate a request across logs and downstream calls.

Tenant-owned requests require `X-Tenant-ID`. The header is only a selector: the backend verifies
that the authenticated OIDC identity has an active database membership before binding tenant roles
to the request. Missing, malformed, unknown, and cross-tenant selections are rejected. Service-area
boundaries accept GeoJSON `Polygon` or `MultiPolygon` values as either JSON objects or JSON strings;
tenant IDs are never accepted in request bodies. Route coordinates use latitude/longitude decimal
degrees and responses report meters and seconds.

Driver onboarding accepts an account UUID only after proving that account has an active membership
in the selected tenant, and grants its persisted membership the `DRIVER` role. Driver and rider
operations resolve the account from the authenticated tenant context. Vehicle and shift mutations
use optimistic versions and return stable `409 resource-conflict` errors for stale or invalid
transitions. Driver document storage is metadata-only; document bytes and raw secrets are never
stored in the marketplace database.

Pricing amounts use signed 64-bit integer minor units and ISO 4217 currency codes. Active pricing
rules cannot overlap for the same tenant and product. Quote creation requires one active service
area to cover both endpoints, obtains distance and duration from OSRM, and snapshots the selected
rule, route, component amounts, adjustments, total, expiry, and request fingerprint. Distance and
duration are rounded up to whole meters and seconds; component multiplication and basis-point
adjustments use deterministic half-up rounding. Quote ownership always comes from the authenticated
tenant context, never from request data. Quotes have no update API and persisted quote fields are
never recalculated when products or rules change. Configure validity with `QUOTE_TTL` (default
`PT10M`).

The backend validates OIDC issuer, audience, signature, expiry, and subject. Configure
`OIDC_ISSUER_URI` and `OIDC_AUDIENCE`; authorization roles are stored in tenant memberships rather
than trusted solely from bearer-token claims.

## Contributing

Read [CONTRIBUTING.md](CONTRIBUTING.md) before opening a pull request. All commits must follow
[Conventional Commits 1.0.0](https://www.conventionalcommits.org/en/v1.0.0/). Participation is
governed by the [Code of Conduct](CODE_OF_CONDUCT.md).

## Security

Do not open public issues for vulnerabilities. Follow [SECURITY.md](SECURITY.md) to report them
privately through GitHub.

## Support

See [SUPPORT.md](SUPPORT.md) for community support channels and scope.

## License

Licensed under the [Apache License 2.0](LICENSE).
