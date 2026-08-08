# Incident And Rollback Runbook

## Triage

1. Assign incident command, record start time and impact, and freeze unrelated deployments.
2. Check readiness, rollout events, structured logs by correlation ID, HTTP error/latency metrics,
   PostgreSQL locks/connections, Redis availability, OIDC discovery/JWKS, and OSRM responses.
3. Reduce harm: pause ingress, scale workers/API, disable a failing integration, or reject writes as
   the incident requires. Preserve logs and audit evidence.

## Rollback

1. Confirm the previous immutable image is compatible with the current database schema.
2. Run `helm history cab -n cab` and inspect the target revision's image and configuration.
3. Run `helm rollback cab <revision> -n cab --wait --timeout 10m`.
4. Verify readiness, synthetic authenticated reads/writes, event processing, metrics, and logs.

Do not use an application rollback to reverse a destructive migration. If compatibility is broken,
stop writers and follow the database restore procedure. Do not delete Redis keys or outbox/inbox
records during triage without a reviewed recovery plan; replay may duplicate external side effects.

After recovery, rotate exposed credentials, reconcile queued events and provider state, communicate
customer impact, and produce a blameless timeline with corrective actions.
