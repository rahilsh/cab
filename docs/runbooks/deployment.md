# Deployment Runbook

## Preconditions

- Build and scan an immutable image from a reviewed commit; deploy by digest.
- Run `./mvnw clean verify`, `docker build`, `docker-compose config`, and Helm lint/template.
- Provision PostgreSQL 17 with PostGIS, Redis 7, OIDC, OSRM, and object storage independently.
- Create the Kubernetes Secret named by `existingSecret.name` with separate restricted application
  and migration credentials; never pass secrets in Helm values.
- Verify database backup recency, migration compatibility, dependency capacity, and alert coverage.

## Deploy

1. Follow [the migration runbook](migrations.md). The chart's migration Job must succeed before the API rollout starts.
2. Render and review manifests with the exact production values and image digest.
3. Ensure `values-production.yaml` sets `existingSecret.name` and `image.digest`, then run `helm upgrade --install cab deploy/helm/cab-marketplace -n cab -f values-production.yaml --wait --wait-for-jobs --timeout 10m`.
4. Check rollout status, pod events, `/actuator/health/readiness`, logs, error rate, latency, and database/Redis saturation.
5. Exercise an authenticated read and a low-risk write using a synthetic tenant.
6. Scale out or enable HPA only after the migration Job succeeded and the first pod is ready.

The chart renders digest deployments as `repository@sha256:...` and rejects production renders
without a digest. NetworkPolicy is enabled by default and denies application egress except DNS;
production values must add narrow rules for PostgreSQL, Redis, OIDC, OSRM, payment providers, and
configured webhooks. Ingress is off by default and requires a non-empty TLS configuration when
enabled unless `ingress.requireTls=false` is an explicit platform decision.

## Outbox Operations

The API process also runs the outbox dispatcher. It polls active tenant IDs from the control plane,
then enters tenant-qualified transactions for all RLS-protected data. Outbox, notification, and
webhook rows use expiring UUID-fenced leases, so replicas can safely compete and recover work after
a crash. Provider calls happen outside database transactions; completion is rejected if the lease
has since been reassigned.

Delivery is at-least-once. Payment calls use stable operation idempotency keys, and notification and
webhook providers must deduplicate by their stable delivery IDs. Alert on increasing `FAILED` rows,
old `PENDING`/`RETRY` rows, exhausted outbox attempts, and repeated provider-unavailable errors.
`OUTBOX_DISPATCHER_ENABLED=false` pauses new dispatch without deleting durable work.

Ride SSE fan-out is also at-least-once. A committed ride update is published immediately through a
tenant-specific Redis channel for low latency, and the durable ride outbox republishes it during
dispatch/retry. Every stream event includes the stable outbox `eventId` and aggregate `version`;
clients must ignore duplicate event IDs and versions they have already applied. New and resumed
connections still receive a full current snapshot before live Redis messages.

Webhook envelopes include `aggregateType`, `aggregateId`, and `aggregateVersion` in addition to the
event metadata and data. Consumers should retain the greatest aggregate version per aggregate and
reject stale deliveries. Webhook delivery remains at-least-once.

The chart deploys the API only. Treat PostgreSQL, Redis, Keycloak/OIDC, OSRM, MinIO/S3, ingress,
certificates, and monitoring as separately managed production services.

## Local Stack

Copy `.env.example` to `.env`, replace the marked development passwords, and run
`docker-compose up --build`. The imported Keycloak realm and all credentials in it are strictly for
local development. MinIO is available for future evidence-object workflows; the current API stores
only external object keys and metadata and does not upload evidence bytes.
