# Security Policy

## Supported Versions

Cab Marketplace is pre-1.0. Until the first stable release, only the latest release and the latest
commit on `main` receive security fixes; older snapshots and feature branches are unsupported.

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

Driver documents contain external object keys and bounded metadata only. Object bytes, fetchable
URLs, and secrets are not accepted. Verification is tenant-admin-only, conditionally changes a
pending document once, attributes the verifier, and writes append-only history. Driver approval
requires a verified driving license that is valid on the approval date.

Ride SSE streams require the rider, assigned driver, or an authorized operations role in the same
tenant. Generic stream events contain status metadata only and are published after transaction
commit, never from a transaction that later rolls back. Connections are bounded and removed on
completion, error, or timeout.

Payment APIs derive tenant, rider, driver, and finance authority from persisted memberships. The
database stores opaque payment-method tokens and provider/configuration references only; PAN, CVV,
bank credentials, API keys, and webhook secrets must never be persisted. Fake-provider callback
secrets are resolved from environment configuration. Provider callbacks are HMAC authenticated over
the timestamp and exact raw body, rejected outside the replay window, account-scoped, idempotent,
and provider-version ordered. Raw callback bodies are not retained.

Provider calls run only from the outbox-driven payment worker, never in ride database transactions.
Payment identity, capture commission splits, and successful refund splits are immutable. Captures
cannot exceed authorization, and refund reservations/successes cannot exceed capture. Refund POSTs
require a scoped idempotency key and store only the safe refund response. Payout submission is not
authoritative: signed, version-ordered callbacks confirm success or release failed reservations.
Financial postings are source-idempotent, balanced, single-currency double entries; database triggers
reject ledger updates and deletes. Zero-fare rides do not create zero-value provider payments.

Rating, support, and safety ownership is derived from tenant-qualified records. Only completed-ride
participants may rate, support roles control triage, and safety reads/actions require `SAFETY` or
`TENANT_ADMIN`. Evidence stores external object references and metadata only. Safety evidence,
actions, support messages/state history, and related audit entries are append-only.

Outbound webhooks require HTTPS and reject loopback, private, link-local, metadata, carrier-grade
NAT, benchmark, and multicast addresses. Each connection is pinned to addresses selected during URL
validation while TLS verifies the original hostname, and redirects are disabled.
Only explicitly allowed non-sensitive events can be subscribed to; payment, live-location, and safety
events are excluded by default. Secrets remain `env:` references. Immutable delivery bodies and
timestamps are HMAC signed over the exact timestamp and body.

## Reporting A Vulnerability

Use GitHub private vulnerability reporting for this repository. Open the repository's **Security**
tab, select **Advisories**, and choose **Report a vulnerability**.

Do not disclose vulnerabilities in public issues, discussions, pull requests, or social media.
Include affected versions, reproduction steps, impact, and suggested remediation when available.

Maintainers will acknowledge a valid report as soon as practical, coordinate remediation and
disclosure with the reporter, and credit reporters who request attribution. Response times are not
guaranteed for this volunteer-maintained project.
