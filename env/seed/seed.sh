#!/usr/bin/env bash
# Seed hook (env/README.md "Seed"). up.sh runs it once per copy, on a fresh database, after mgmt-server is up and
# before the seed dump that reset.sh restores. Replace it with APP_ENV_SEED=<script>, or APP_ENV_SEED=none for an
# empty seed (fresh schema only). Gets from up.sh:
#   SEED_NETWORK      the copy's internal Docker network (no route out)
#   SEED_RESOLV       resolv.conf for that network (the copy's logging DNS)
#   SEED_MGMT_URL     Management API base URL on that network, e.g. http://<project>-mgmt:8080
#   SEED_CREDENTIALS  env file with MGMT_USER and MGMT_PASSWORD for the Management API
#   SEED_LABEL        label to put on any container it starts
# Exit 0 when the seed is in place.
set -u -o pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
exec docker run --rm --network "$SEED_NETWORK" --label "$SEED_LABEL" \
  -v "$SEED_RESOLV:/etc/resolv.conf:ro" -v "$HERE/seed.py:/seed.py:ro" --env-file "$SEED_CREDENTIALS" \
  python:3.9.6-slim python -u /seed.py "$SEED_MGMT_URL"
