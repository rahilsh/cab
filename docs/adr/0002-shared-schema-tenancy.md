# ADR 0002: Shared-Schema Multi-Tenancy

- Status: Accepted
- Date: 2026-08-08

## Context

One deployment must host multiple cab operators without exposing data across tenants.

## Decision

Use a shared PostgreSQL schema with `tenant_id` on every tenant-owned row. The authenticated user
selects a tenant with `X-Tenant-ID`; the value is accepted only after membership validation.
Repositories require tenant-qualified operations, foreign keys include tenant identity, Redis keys
are namespaced, and PostgreSQL row-level security provides defense in depth.

## Consequences

Every background job and outbox event must carry tenant identity. Integration tests must prove
cross-tenant reads and writes fail. Platform support access requires a dedicated audited workflow.
