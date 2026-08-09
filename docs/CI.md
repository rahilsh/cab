# CI And Repository Rules

GitHub Actions are pinned to full commit SHAs with version comments. Dependabot proposes weekly,
grouped Maven, GitHub Actions, and Docker updates. Only patch updates from Dependabot are eligible for
auto-merge, and GitHub waits for the branch's required reviews and checks before merging.

## Required Ruleset

Repository settings cannot be enforced from this repository. Protect `main` with a GitHub branch
ruleset that:

- Requires pull requests, at least one approval, CODEOWNERS review, and dismissal of stale approvals.
- Requires conversation resolution and blocks force pushes and deletions.
- Requires branches to be current before merge and requires linear history.
- Requires `Java CI / build`, `Java CI / deployment-assets`, `Dependency review / review`, `CodeQL / analyze`, and `PR title / conventional-commit`.
- Prevents bypass except for a documented emergency maintainer role.
- Restricts workflow-file changes to CODEOWNERS review.

Enable repository auto-merge so the patch-only Dependabot workflow can request it. Do not make the
Dependabot auto-merge workflow itself a required check because it intentionally skips non-Dependabot
pull requests and non-patch updates.

Add a tag ruleset for `v*` that prevents updates and deletions and restricts tag creation to release
maintainers. Require maintainers to sign release tags as described in `RELEASING.md`.

## Workflow Scope

- `maven.yml` runs `./mvnw clean verify` with Docker available for Testcontainers and always retains test and coverage reports.
- Its deployment-assets job runs actionlint, validates Compose, builds the Dockerfile, and lints and renders the Helm chart with digest-pinned tool containers.
- `dependency-review.yml` blocks newly introduced high-severity vulnerable dependencies and selected strong-copyleft licenses.
- `codeql.yml` analyzes Java on pull requests, `main`, and weekly.
- `release.yml` accepts annotated semantic tags reachable from `origin/main`, builds release artifacts and a GHCR image, and never deploys.

Fork pull requests receive read-only tokens. Workflows that write packages, attestations, or releases
run only for a maintainer-created release tag. The `pull_request_target` Dependabot workflow never
checks out or executes pull request code and passes untrusted values through environment variables.
