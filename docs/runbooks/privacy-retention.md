# Privacy and data retention

## Automated location purge

Driver location checkpoints are operational telemetry, not a permanent ride record. The scheduled
purge removes checkpoints older than `LOCATION_RETENTION_DURATION` (default `P30D`) in bounded,
tenant-isolated batches. Configure `LOCATION_RETENTION_FIXED_DELAY` in milliseconds and
`LOCATION_RETENTION_BATCH_SIZE`; set `LOCATION_RETENTION_ENABLED=false` only during controlled
maintenance. Redis live-location entries remain short-lived dispatch state and are not an archive.

Monitor purge failures in application logs and database growth. A legal hold that covers location
data must be approved and documented before disabling the purge, with an owner and expiry date.

## Delivery attempts

Notification and webhook attempt metadata has operator-configurable policy values
`NOTIFICATION_ATTEMPT_RETENTION` and `WEBHOOK_ATTEMPT_RETENTION` (defaults `P90D`). Attempts are
append-only evidence and are not deleted by the application scheduler. Operators must implement an
approved archival/deletion job against these values, preserving delivery audit requirements and
tenant isolation. Do not delete parent delivery or outbox records while retained attempts refer to
them.

## Support and safety records

Support cases/messages and safety incidents/evidence/actions are retained according to the
operator's documented legal, regulatory, insurance, and litigation-hold policy. The application
does not automatically purge these records. Evidence object lifecycle rules must match the database
metadata policy. Access and deletion requests require legal review, audit recording, and coordinated
removal of external evidence objects, backups, and derived exports.

Review all durations at least annually and whenever the operating jurisdiction or product purpose
changes. Test purge and restore procedures in a non-production environment before policy changes.
