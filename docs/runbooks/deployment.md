# Deployment Runbook

## Preconditions

- Build and scan an immutable image from a reviewed commit; deploy by digest or immutable tag.
- Run `./mvnw clean verify`, `docker build`, `docker-compose config`, and Helm lint/template.
- Provision PostgreSQL 17 with PostGIS, Redis 7, OIDC, OSRM, and object storage independently.
- Create the Kubernetes Secret named by `existingSecret.name` with separate restricted application
  and migration credentials; never pass secrets in Helm values.
- Verify database backup recency, migration compatibility, dependency capacity, and alert coverage.

## Deploy

1. Follow [the migration runbook](migrations.md). Keep the API at one replica during startup migrations.
2. Render and review manifests with the exact production values and image digest.
3. Run `helm upgrade --install cab deploy/helm/cab-marketplace -n cab -f values-production.yaml --wait --timeout 10m`.
4. Check rollout status, pod events, `/actuator/health/readiness`, logs, error rate, latency, and database/Redis saturation.
5. Exercise an authenticated read and a low-risk write using a synthetic tenant.
6. Scale out or enable HPA only after the first pod is ready and Flyway has completed.

The chart deploys the API only. Treat PostgreSQL, Redis, Keycloak/OIDC, OSRM, MinIO/S3, ingress,
certificates, and monitoring as separately managed production services.

## Local Stack

Copy `.env.example` to `.env`, replace the marked development passwords, and run
`docker-compose up --build`. The imported Keycloak realm and all credentials in it are strictly for
local development. MinIO is available for future evidence-object workflows; the current API stores
only external object keys and metadata and does not upload evidence bytes.
