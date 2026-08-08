# Cab Marketplace

[![Java CI](https://github.com/rahilsh/cab/actions/workflows/maven.yml/badge.svg)](https://github.com/rahilsh/cab/actions/workflows/maven.yml)
[![CodeQL](https://github.com/rahilsh/cab/actions/workflows/codeql.yml/badge.svg)](https://github.com/rahilsh/cab/actions/workflows/codeql.yml)

Cab Marketplace is an open-source, production-oriented modular monolith for operating multi-tenant
ride-hailing services across operators, riders, drivers, vehicles, pricing, dispatch, trips,
payments, and settlements. The project remains pre-1.0, so APIs and deployment contracts may change
between releases.

The customer and operator frontends will be maintained in separate repositories. This repository
contains the HTTP API and backend services only.

## Project Status

The current implementation supports:

- PostgreSQL/PostGIS persistence managed by Flyway
- RFC 9457 validation errors, correlation IDs, and health probes
- OIDC-protected tenant provisioning and operator memberships
- Tenant-owned PostGIS service areas
- OSRM-backed route distance and duration estimates
- Tenant-scoped rider and driver profiles, document verification, driver approval, vehicles, and driver shifts
- Tenant-scoped service products, versioned pricing rules, and immutable rider fare quotes
- Tenant-scoped idempotency, transactional outbox/inbox, and append-only audit foundations
- Tenant-scoped live driver locations, dispatch offers, and the complete ride lifecycle
- Authorized server-sent ride status updates for participants and operations staff
- Provider-neutral capture/refund processing, configurable commission, immutable double-entry ledger, earnings, and settlements
- Provider-neutral local notifications, participant ratings, support/safety workflows, and signed outbound webhooks

The production roadmap includes:

- Broader role-based access and audited platform-support workflows
- External object-storage integration for document upload orchestration
- Production notification and payment-provider adapters
- Expanded moderation, support automation, and safety escalation workflows
- Production payment/notification adapters and additional operational hardening

See [the production roadmap](docs/ROADMAP.md) for sequencing and acceptance criteria.

## Architecture

The architecture is a package-modular Spring Boot monolith:

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
- Generates a CycloneDX JSON SBOM at `target/classes/META-INF/sbom/application.cdx.json`
- Runs unit tests
- Enforces at least 85% aggregate unit-test line coverage
- Starts the application on a random real port for HTTP integration tests

Coverage is written to `target/site/jacoco/index.html`. Integration tests use the `*IT` suffix and
are run by Maven Failsafe. MockMvc tests do not qualify as API integration tests.

## Quick Start

The complete local stack includes the API, PostgreSQL/PostGIS, Redis, Keycloak, OSRM, and MinIO:

```bash
cp .env.example .env
# Replace every dev-only password in .env before starting.
docker-compose up --build
```

The default OSRM download is the small Monaco extract. Preparation runs once and is cached in a
named volume; see [the OSRM runbook](docs/runbooks/osrm-data.md) before selecting a larger region.
The Keycloak realm contains clearly named local users with `dev-only-*` passwords. These users,
passwords, direct password grants, and `start-dev` mode must never be used outside local development.
The API validates the public Keycloak issuer while retrieving signing keys on the private Compose
network. Split-network deployments must configure both `OIDC_ISSUER_URI` and `OIDC_JWK_SET_URI`.

Obtain a local platform administrator token:

```bash
curl --request POST http://localhost:8081/realms/cab/protocol/openid-connect/token \
  --data grant_type=password \
  --data client_id=cab-local-cli \
  --data username=platform-admin \
  --data password=dev-only-platform-admin \
  --data 'scope=openid platform.admin observability.read'
```

Local users are `platform-admin`, `operator`, `driver`, and `rider`; their matching realm roles are
for identity testing only. Marketplace tenant roles remain authoritative in PostgreSQL. MinIO is
available at `http://localhost:9000` for future evidence-object integration, while the current API
stores external evidence references only.

The application reads `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD`, `REDIS_HOST`,
`REDIS_PORT`, and `OSRM_BASE_URL`. API documentation is disabled by default; set
`API_DOCS_ENABLED=true` and optionally `SWAGGER_UI_ENABLED=true` to expose `/v3/api-docs` and
`/swagger-ui.html`. Payment settings use `PAYMENT_PROVIDER`,
`FAKE_PAYMENT_CONFIG_REFERENCE`, `FAKE_PAYMENT_WEBHOOK_SECRET_REFERENCE`,
`FAKE_PAYMENT_WEBHOOK_SECRET`, and `PAYMENT_WEBHOOK_TOLERANCE`. OSRM defaults to
`http://localhost:5000`. Defaults are intended
for local development only. Flyway applies pending migrations; Hibernate validates the resulting
schema and never creates or drops production tables. The runtime database credentials must identify
a non-owner role without `BYPASSRLS`; Flyway uses `MIGRATION_DATABASE_USERNAME` and
`MIGRATION_DATABASE_PASSWORD` as described in the migration runbook.

## Deployment

`Dockerfile` builds a layered Java 21 image, runs as UID/GID `10001`, includes OCI metadata and a
healthcheck, and supports a read-only root filesystem with writable `/tmp`. Build it with:

```bash
docker build --tag cab-marketplace:local .
```

The Helm chart at `deploy/helm/cab-marketplace` deploys the stateless API with actuator probes,
resource defaults, non-root/read-only security contexts, and optional ingress, HPA, PDB, and network
policy. It references an externally managed Kubernetes Secret and contains no credential defaults.
Production dependencies are intentionally not bundled into the chart.

Read the operations runbooks before deployment:

- [Deployment](docs/runbooks/deployment.md)
- [Migrations](docs/runbooks/migrations.md)
- [Backup and restore](docs/runbooks/backup-restore.md)
- [Incident response and rollback](docs/runbooks/incident-rollback.md)
- [OSRM data preparation](docs/runbooks/osrm-data.md)

## API

The HTTP API is versioned under `/api/v1`:

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
| `POST` | `/api/v1/drivers/{id}/suspend` | Suspend a driver; requires `TENANT_ADMIN` |
| `GET`, `PUT` | `/api/v1/drivers/me` | Read or update the authenticated driver profile |
| `POST`, `GET` | `/api/v1/drivers/me/documents` | Submit or list the authenticated driver's document metadata |
| `GET` | `/api/v1/drivers/{id}/documents` | List driver documents; requires `TENANT_ADMIN` |
| `POST` | `/api/v1/drivers/{id}/documents/{documentId}/verify`, `/reject` | Review a pending document; requires `TENANT_ADMIN` |
| `POST`, `GET` | `/api/v1/vehicles` | Create or list vehicles; requires `TENANT_ADMIN` |
| `PUT` | `/api/v1/vehicles/{id}` | Versioned vehicle update; requires `TENANT_ADMIN` |
| `POST`, `GET` | `/api/v1/driver/shifts` | Create or list the authenticated driver's shifts |
| `POST` | `/api/v1/driver/shifts/{id}/go-online` | Move an `OFFLINE` shift to `AVAILABLE` |
| `POST` | `/api/v1/driver/shifts/{id}/go-offline` | Move an `AVAILABLE` shift to `OFFLINE` |
| `POST` | `/api/v1/driver/shifts/{id}/close` | Close an offline shift |
| `POST` | `/api/v1/current-tenant/roles/{role}` | Admin self-grant of `RIDER` or `DRIVER` for operations/testing |
| `POST`, `GET` | `/api/v1/products` | Create or list service products; requires `TENANT_ADMIN` |
| `POST`, `GET` | `/api/v1/pricing-rules` | Create or list versioned pricing rules; requires `TENANT_ADMIN` |
| `POST`, `GET` | `/api/v1/quotes` | Create or list the authenticated rider's immutable fare quotes |
| `GET` | `/api/v1/quotes/{id}` | Read one fare quote owned by the authenticated rider |
| `GET` | `/api/v1/admin/audit-events` | List tenant audit events; requires `TENANT_ADMIN` or `SUPPORT` |
| `PUT` | `/api/v1/driver/location` | Atomically publish a fresh location for the authenticated driver's available shift |
| `POST`, `GET` | `/api/v1/rides` | Create a ride from an owned quote or list the authenticated rider's rides |
| `GET`, `POST` | `/api/v1/rides/{id}` | Read an owned ride or cancel it at `/cancel` |
| `GET` | `/api/v1/rides/{id}/events` | Stream authorized ride status events with SSE |
| `POST` | `/api/v1/dispatch/rides/{id}/start` | Search bounded fresh supply and create expiring offers; requires `TENANT_ADMIN` or `DISPATCHER` |
| `GET` | `/api/v1/driver/offers` | List the authenticated driver's pending, unexpired offers |
| `POST` | `/api/v1/driver/offers/{id}/accept`, `/reject` | Accept or reject a dispatch offer |
| `POST` | `/api/v1/driver/rides/{id}/{action}` | Apply `arriving`, `arrive`, `start`, `complete`, or `cancel` lifecycle actions |
| `POST` | `/api/v1/dispatch/rides/{id}/cancel` | Administratively cancel a ride |
| `GET` | `/api/v1/payments/{id}` | Read payment status by payment ID |
| `GET` | `/api/v1/rides/{id}/payment` | Read the authenticated rider's payment status |
| `POST` | `/api/v1/finance/payments/{id}/refunds` | Request a bounded refund; requires `FINANCE` or `TENANT_ADMIN` |
| `GET` | `/api/v1/finance/refunds/{id}` | Read a refund; requires finance access |
| `GET` | `/api/v1/driver/earnings` | Read the authenticated driver's ledger balance |
| `POST`, `GET` | `/api/v1/finance/settlements` | Settle positive driver balances or list batches; creation requires `FINANCE` |
| `POST` | `/api/v1/payment-providers/{provider}/accounts/{id}/events` | Receive signed provider callbacks |
| `PUT`, `GET` | `/api/v1/notification-preferences` | Manage rider/driver event and channel preferences |
| `POST` | `/api/v1/rides/{id}/ratings` | Rate the other participant after a completed ride |
| `POST`, `GET` | `/api/v1/support/cases` | Create an owned case or list visible tenant cases |
| `POST` | `/api/v1/support/cases/{id}/state` | Triage a case; requires `SUPPORT` or `TENANT_ADMIN` |
| `POST` | `/api/v1/support/cases/{id}/messages`, `/assignments` | Add a case message or assignment |
| `POST`, `GET` | `/api/v1/safety/incidents` | Participant reporting and restricted safety listing |
| `POST` | `/api/v1/safety/incidents/{id}/evidence` | Add external evidence metadata; no bytes or URLs |
| `POST` | `/api/v1/safety/incidents/{id}/actions` | Apply audited restricted safety actions |
| `POST`, `GET`, `PUT`, `DELETE` | `/api/v1/admin/webhook-subscriptions` | Manage tenant outbound webhooks |

Operational probes are available at `/actuator/health/liveness` and
`/actuator/health/readiness`. API responses include `X-Correlation-ID`; clients may supply this
header to correlate a request across logs and downstream calls.

Health probes are public. `/actuator/prometheus` and `/actuator/info` require a bearer token with
the `observability.read` scope. Prometheus includes standard HTTP/JVM metrics and rate-limit outcome
counters. The `prod` profile emits Logstash-compatible structured JSON with safe MDC fields for the
correlation ID and authorized tenant/account UUIDs; request bodies, tokens, and idempotency keys are
never added to MDC.

Authenticated `/api/v1` tenant operations are rate limited by tenant and account using an atomic
Redis counter. Configure `RATE_LIMIT_ENABLED`, `RATE_LIMIT_REQUESTS` (default `120`), and
`RATE_LIMIT_WINDOW` (default `PT1M`). Rejections use RFC problem JSON, status `429`, and
`Retry-After`; health probes, tenant provisioning/listing, and signed provider callbacks are not
included in this tenant limiter. Responses expose `RateLimit-Limit`, `RateLimit-Remaining`, and
`RateLimit-Reset` headers.

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
transitions. Drivers submit bounded document type/reference/object-key/expiry metadata; admins can
verify or reject it once, with verifier attribution and append-only history. Object keys reject URLs
and traversal. Document bytes, fetchable URLs, and raw secrets are never accepted. A license remains
valid through its `expiresOn` date; a null expiry has no stated expiration. Approval requires at
least one verified, non-expired `DRIVING_LICENSE`.

Live locations require an owned `AVAILABLE` shift and monotonically increasing sequence and device
timestamp. Redis keys are tenant namespaced; a Lua script atomically updates GEO membership and
sequence/timestamp metadata. Dispatch bounds candidates, filters stale locations, then rechecks
authoritative shift, vehicle, driver, and product eligibility in PostgreSQL. Redis remains
ephemeral; checkpoints provide a tenant-qualified operational trail.

Ride creation consumes an owned, active, unexpired quote with one conditional database update and
snapshots its product, coordinates, total fare, and currency. It requires `Idempotency-Key` and
writes initial history, outbox, audit, and idempotency completion in the same transaction. Ride and
shift transitions are optimistic. Offer acceptance locks only the chosen offer and conditionally
reserves its shift; database partial unique constraints enforce one accepted offer per ride and one
active ride per shift. Completion and allowed cancellations release the shift to `AVAILABLE`.

Ride participants and `TENANT_ADMIN`, `DISPATCHER`, or `SUPPORT` members can stream status changes
from `/api/v1/rides/{id}/events`. Events contain only ride ID, status, optimistic version, and event
time; generic events never expose live location. Publication is registered after transaction commit,
and connections have per-actor/per-ride bounds and timeouts. The registry is in-process and therefore
per application instance. Multi-replica deployments require external pub/sub (or a future outbox
poller bridge) so every instance can deliver events to its local connections.

Ride completion creates a `CAPTURE_PENDING` payment and transactional
`payment.capture_requested` outbox event. It never calls a provider inside the ride transaction.
An outbox consumer invokes the callable `PaymentOperationWorker`; provider success is authoritative
only after a signed callback. The included `FakePaymentProvider` is for local development and tests.
Callbacks use HMAC-SHA256 over `<unix-seconds>.<raw-body>`, enforce a configurable replay window,
deduplicate by payment account and provider event ID, and ignore stale provider versions. Callback
rows retain normalized identifiers and monetary metadata, not raw provider bodies.

Capture posts a balanced ledger transaction from provider receivable to driver payable and platform
revenue according to `PAYMENTS_PLATFORM_COMMISSION_BASIS_POINTS` (15% by default). Successful
refunds reverse both shares proportionally, and completed payouts move driver payable to payout clearing. Ledger
transactions and entries are append-only, source-idempotent, single-currency, and checked for equal
debits and credits at commit. Refund reservations and successful refunds cannot exceed capture.

Pricing amounts use signed 64-bit integer minor units and ISO 4217 currency codes. Active pricing
rules cannot overlap for the same tenant and product. Quote creation requires one active service
area to cover both endpoints, obtains distance and duration from OSRM, and snapshots the selected
rule, route, component amounts, adjustments, total, expiry, and request fingerprint. Distance and
duration are rounded up to whole meters and seconds; component multiplication and basis-point
adjustments use deterministic half-up rounding. Quote ownership always comes from the authenticated
tenant context, never from request data. Quotes have no update API and persisted quote fields are
never recalculated when products or rules change. Configure validity with `QUOTE_TTL` (default
`PT10M`).

`POST /api/v1/quotes` requires an `Idempotency-Key` of 1 to 255 characters. Keys are scoped by
tenant, authenticated account, and operation. Repeating the same canonical request returns the
original `201` response without recalculating or writing another quote; reusing a live key with a
different request returns `409 idempotency-key-reused`. Records expire after `IDEMPOTENCY_TTL`
(default `PT24H`). Only a response representation explicitly selected by the calling service is
stored, never arbitrary servlet responses, headers, bearer tokens, or unredacted request bodies.

Quote creation writes the quote, completed idempotency record, `fare_quote.created` outbox event,
and `fare_quote.create` audit event in one database transaction. Outbox payloads and audit summaries
must be deliberately minimized by callers. Outbox consumers lease tenant-qualified batches using
`FOR UPDATE SKIP LOCKED`; expired leases can be reclaimed, retries clear lease state, and permanent
failures remain inspectable. Inbox receipts deduplicate by tenant, consumer, and event ID. Audit
events are append-only: the database rejects updates and deletes. The audit list is tenant-scoped,
newest-first, and limited to 200 rows per request.

Notification preferences are scoped by tenant, recipient, event, and channel. The provider port has
only a local logging adapter in this release. Delivery snapshots and attempts are durable and unique;
transactional payment/cancellation and safety notifications bypass user opt-outs. Inbox receipts
prevent an outbox replay from repeating a completed delivery.

Ratings require a completed ride and resolve both participants from tenant-qualified records. Each
participant may submit one score from 1 to 5. Support cases are visible to their creator or support
staff; only `SUPPORT`/`TENANT_ADMIN` can assign or transition them. Safety reports require ride
participation, while listing and actions require `SAFETY`/`TENANT_ADMIN`. Evidence stores external
object keys and bounded metadata, never content or fetchable URLs.

Webhook subscriptions accept HTTPS URLs and `env:NAME` secret references only. Filters use an
explicit non-sensitive allowlist; payment, live-location, and safety events are excluded by default.
URL validation rejects non-public addresses, DNS is resolved again before every request, and
redirects are disabled. Immutable deliveries are HMAC-SHA256 signed over
`<unix-seconds>.<exact-body>` and retry with bounded exponential backoff.

The backend validates OIDC issuer, audience, signature, expiry, and subject. Configure
`OIDC_ISSUER_URI` and `OIDC_AUDIENCE`; authorization roles are stored in tenant memberships rather
than trusted solely from bearer-token claims.

Correlation IDs are bounded to 128 characters and flow through request metadata into outbox and
audit records. Sensitive values must not be placed in correlation IDs, idempotency keys, event
payloads, audit summaries, or retry error text.

## Contributing

Read [CONTRIBUTING.md](CONTRIBUTING.md) before opening a pull request. All commits must follow
[Conventional Commits 1.0.0](https://www.conventionalcommits.org/en/v1.0.0/). Participation is
governed by the [Code of Conduct](CODE_OF_CONDUCT.md).

CI behavior and required branch protections are documented in [CI and repository rules](docs/CI.md).
See the [changelog](CHANGELOG.md), [compatibility policy](docs/COMPATIBILITY.md), and
[release process](RELEASING.md) for release standards. Release automation publishes artifacts and a
GHCR image but deliberately performs no cloud deployment.

## Security

Do not open public issues for vulnerabilities. Follow [SECURITY.md](SECURITY.md) to report them
privately through GitHub.

## Support

See [SUPPORT.md](SUPPORT.md) for community support channels and scope.

## License

Licensed under the [Apache License 2.0](LICENSE).
