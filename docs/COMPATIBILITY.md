# Versioning And Compatibility

Cab Marketplace follows Semantic Versioning after the first stable release. Before `1.0.0`, minor
versions may contain breaking changes and patch versions remain backward compatible within the same
minor line. The current prototype supports only the latest commit on `main` for security fixes.

## Compatibility

- Patch releases contain backward-compatible fixes and security updates.
- Minor releases add backward-compatible behavior. Before `1.0.0`, they may also remove previously deprecated prototype behavior.
- Major releases may make incompatible API, configuration, persistence, event, or operational changes.
- Public `/api/v1` request and response contracts, environment variable names, database migrations, emitted event schemas, and supported Java/runtime requirements are compatibility surfaces.
- Additive response fields are compatible; clients must ignore unknown fields. Removing fields, narrowing accepted values, or changing meaning is breaking.
- Flyway migrations are forward-only. A release must remain compatible with the schema produced by all migrations included in that release; applied migrations are never edited.
- Persisted data and externally consumed events require an explicit migration or transition path. Silent destructive conversion is not allowed.

## Deprecation

Document a deprecation in the changelog, API documentation, and migration guidance. State the
replacement and earliest removal version. After `1.0.0`, keep deprecated public behavior through at
least one minor release and for at least 90 days when practical; security, legal, or data-integrity
issues may require faster removal and must be called out prominently.

Breaking changes require a Conventional Commit `!` marker or `BREAKING CHANGE:` footer, a changelog
entry, migration instructions, and the version increment required by this policy. Experimental or
explicitly internal interfaces may change without a deprecation window but still require release notes.
