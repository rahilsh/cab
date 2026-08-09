# ADR 0001: Package-Modular Monolith

- Status: Accepted
- Date: 2026-08-08

## Context

The system needs transactional consistency across dispatch, rides, payments, and audit records,
while remaining straightforward for self-hosters to operate.

## Decision

Build one Spring Boot deployment with business capability packages and explicit public module
interfaces. PostgreSQL is shared, but modules do not access another module's repositories or JPA
entities. Asynchronous side effects use a transactional outbox.

## Consequences

Deployment and local development remain simple. Module boundaries require automated architecture
tests. A module can be extracted later only when operational evidence justifies it.
