# Contributing

Thank you for improving Cab Marketplace.

## Before You Start

- Search existing issues and pull requests.
- Open an issue for significant behavior, schema, API, or architecture changes.
- Never include secrets, production data, or personally identifiable information.

## Development

Use Java 21 and the included Maven Wrapper:

```bash
./mvnw clean verify
```

On macOS with Colima:

```bash
DOCKER_HOST="unix://${HOME}/.colima/default/docker.sock" \
TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock \
./mvnw clean verify
```

Every change must include tests. Unit-test line coverage must remain above 85%. API integration
tests must use a random real HTTP port and an HTTP client rather than MockMvc.

## Commits

Follow Conventional Commits 1.0.0:

```text
feat(dispatch): add expiring driver offers
fix(tenancy): reject cross-tenant ride lookup
docs: document local OIDC setup
```

Use `!` and a `BREAKING CHANGE:` footer for incompatible changes.

## Pull Requests

- Keep changes focused and independently deployable.
- Explain user-visible behavior and migration impact.
- Update the README, OpenAPI contract, and operations documentation when applicable.
- Add Flyway migrations for schema changes; never edit an applied migration.
- Ensure all required checks pass.

By contributing, you agree that your contributions are licensed under Apache-2.0.
