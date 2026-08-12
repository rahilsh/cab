# Security Policy

## Supported Versions

Until the first stable release, only the latest commit on `main` receives security fixes. The
prototype must not be exposed publicly or used for real passenger, driver, payment, or trip data.

Tenant-owned pricing and quote operations require a verified `X-Tenant-ID` membership. Product and
pricing-rule administration requires the persisted `TENANT_ADMIN` role; quote operations require
the persisted `RIDER` role and derive rider ownership from the authenticated tenant context. Tenant
and rider identifiers are not accepted in quote request bodies. Monetary arithmetic is checked for
overflow, and quote snapshots are immutable through the HTTP API.

Fare quote creation requires a tenant/account/operation-scoped idempotency key. Only a
caller-selected safe response is retained for replay; raw requests, authorization headers, and
arbitrary responses must never be stored. Outbox payloads and audit summaries must be explicitly
minimized and redacted. Correlation IDs are bounded and must not contain secrets. Audit reads
require `TENANT_ADMIN` or `SUPPORT`, all operational queries are tenant-qualified, and database
triggers reject audit updates and deletes.

Driver location, offer, and ride operations derive ownership from the selected tenant membership.
Location updates are freshness and sequence checked and stored under tenant-namespaced Redis keys.
Dispatch always rechecks PostgreSQL supply eligibility; Redis candidates are hints, not authority.
Quote consumption, offer acceptance, ride assignment, shift reservation, history, outbox, and audit
writes use database transactions and conditional updates. Fare and currency snapshots are never
accepted from clients or recalculated during the lifecycle.

## Reporting A Vulnerability

Use GitHub private vulnerability reporting for this repository. Open the repository's **Security**
tab, select **Advisories**, and choose **Report a vulnerability**.

Do not disclose vulnerabilities in public issues, discussions, pull requests, or social media.
Include affected versions, reproduction steps, impact, and suggested remediation when available.

Maintainers will acknowledge a valid report as soon as practical, coordinate remediation and
disclosure with the reporter, and credit reporters who request attribution. Response times are not
guaranteed for this volunteer-maintained project.
