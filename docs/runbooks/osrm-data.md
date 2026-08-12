# OSRM Data Preparation Runbook

Compose defaults to the small Monaco Geofabrik extract so local startup is practical. The
`osrm-prepare` one-shot service downloads the PBF and creates MLD files in the `osrm-data` volume;
subsequent starts reuse the files.

## Local Region Change

1. Choose the smallest trusted `.osm.pbf` extract covering the intended service area.
2. Set `OSRM_PBF_URL` in `.env`.
3. Stop the stack and remove only the derived OSRM volume with
   `docker volume rm <compose-project>_osrm-data`.
4. Run `docker-compose up osrm-prepare`, then `docker-compose up osrm`.
5. Query representative routes and boundary cases before starting the API.

Large regions require substantial RAM, CPU, disk, and preparation time. Do not use the Compose
one-shot container as a production data pipeline.

## Production Dataset

1. Pin the PBF source and checksum, OSRM image digest, and routing profile in change control.
2. Download over TLS, verify checksum, then run `osrm-extract`, `osrm-partition`, and
   `osrm-customize` in a capacity-controlled build job.
3. Publish the complete generated dataset as a versioned, immutable artifact.
4. Start a canary OSRM instance, validate representative routes and latency, then atomically switch
   API traffic or the OSRM service selector.
5. Retain the previous dataset for fast rollback and monitor `NoRoute`/error rates after promotion.

OSRM readiness should test a route inside the loaded region. Update the Compose healthcheck sample
coordinates when replacing Monaco.
