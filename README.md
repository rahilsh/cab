# Cab Marketplace

Cab Marketplace is an open-source backend for operating multi-tenant ride-hailing services. The
project is evolving from a single-fleet prototype into a production-oriented modular monolith for
operators, riders, drivers, vehicles, pricing, dispatch, trips, payments, and settlements.

> [!WARNING]
> The current code is an experimental prototype. It has no authentication or tenant isolation and
> uses an ephemeral development database. Do not expose it publicly or use it for real bookings.

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

The production roadmap includes:

- OIDC authentication, role-based access, and strict tenant isolation
- Rider, driver, vehicle, service-area, and compliance management
- Fare quotes, OSRM routing, dispatch offers, and complete trip lifecycle
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

The application reads `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD`, `REDIS_HOST`, and
`REDIS_PORT`. Defaults are intended for local development only. Flyway applies pending migrations;
Hibernate validates the resulting schema and never creates or drops production tables.

## API

The current unversioned prototype endpoints are temporary:

| Method | Path | Purpose |
| --- | --- | --- |
| `POST` | `/cities` | Register a city |
| `GET` | `/cities` | List cities |
| `POST` | `/cabs` | Register a cab |
| `POST` | `/cabs/{cabId}` | Update a cab |
| `GET` | `/cabs` | List cabs |
| `POST` | `/bookings` | Create a booking |
| `GET` | `/bookings` | List bookings |

Operational probes are available at `/actuator/health/liveness` and
`/actuator/health/readiness`. API responses include `X-Correlation-ID`; clients may supply this
header to correlate a request across logs and downstream calls.

These routes will be replaced by the authenticated `/api/v1` marketplace contract. Consumers
must not rely on the prototype API.

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
