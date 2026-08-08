# Releasing

Releases use signed, annotated Semantic Versioning tags and are built only by GitHub Actions. The
release workflow publishes a verified JAR, CycloneDX SBOM, SHA-256 checksums, a vulnerability-scanned
GHCR image, and GitHub artifact attestations. It does not deploy to any environment.

## Prepare

1. Confirm `main` is protected as described in [the CI policy](docs/CI.md) and all required checks pass.
2. Choose the version according to [the compatibility policy](docs/COMPATIBILITY.md).
3. Move relevant entries from `Unreleased` in `CHANGELOG.md` into a dated version section and update comparison links.
4. Set the Maven project version and Helm `appVersion`; increment the chart `version` when chart content changed.
5. Run `./mvnw clean verify`, `docker build --tag cab-marketplace:release-candidate .`, `docker compose config`, and `helm lint deploy/helm/cab-marketplace`.
6. Merge the focused release-preparation pull request after review and required checks.

## Sign And Publish

Create a signed annotated tag from the reviewed `main` commit. Never move or reuse a release tag.

```bash
git switch main
git pull --ff-only
git tag --sign v1.2.3 --message "Cab Marketplace v1.2.3"
git tag --verify v1.2.3
git push origin v1.2.3
```

The tag starts `.github/workflows/release.yml`. A maintainer may rerun it with `workflow_dispatch`
only by naming an existing tag. The workflow refuses non-semantic tags and existing GitHub releases.
Protect `v*` tags with a GitHub ruleset that limits tag creation and deletion to maintainers.

After publication, verify the release assets, checksum file, image digest, and attestations. Consumers
should pull GHCR images by digest and verify local assets with `shasum -a 256 -c SHA256SUMS`. GitHub
CLI users can verify provenance with `gh attestation verify <artifact> --repo rahilsh/cab`.

## Correct A Release

Published tags and assets are immutable. If a release is faulty, leave it available for auditability,
mark it as affected in the changelog or advisory, and publish a new patch version. Follow the incident
and rollback runbook for deployed environments; release automation never performs rollback or deploy.
