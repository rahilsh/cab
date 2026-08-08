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

The production roadmap includes:

- OIDC authentication, role-based access, and strict tenant isolation
- PostgreSQL/PostGIS and versioned Flyway migrations
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

Maven does not need to be installed because the repository includes a pinned Maven Wrapper.

## Build And Test

```bash
./mvnw clean verify
```

This command:

- Compiles and packages an executable Spring Boot JAR
- Runs unit tests
- Enforces at least 85% aggregate unit-test line coverage
- Starts the application on a random real port for HTTP integration tests

Coverage is written to `target/site/jacoco/index.html`. Integration tests use the `*IT` suffix and
are run by Maven Failsafe. MockMvc tests do not qualify as API integration tests.

Run the current prototype locally:

```bash
./mvnw spring-boot:run
```

The prototype expects Redis at `localhost:6379`. Its H2 database is recreated on every start.

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
