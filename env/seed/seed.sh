#!/usr/bin/env bash
#
# Copyright (c) 2023 Contributors to the Eclipse Foundation
#
# This program and the accompanying materials are made
# available under the terms of the Eclipse Public License 2.0
# which is available at https://www.eclipse.org/legal/epl-2.0/
#
# SPDX-License-Identifier: EPL-2.0
#

# Seed hook (env/README.md "Seed"). up.sh runs it once per copy, on a fresh database, after mgmt-server is up and
# before the seed dump that reset.sh restores. Replace it with APP_ENV_SEED=<script>, or APP_ENV_SEED=none for an
# empty seed (fresh schema only). Gets from up.sh:
#   SEED_NETWORK      the copy's internal Docker network (no route out)
#   SEED_RESOLV       resolv.conf for that network (the copy's logging DNS)
#   SEED_MGMT_URL     Management API base URL on that network, e.g. http://<project>-mgmt:8080
#   SEED_CREDENTIALS  env file with MGMT_USER and MGMT_PASSWORD for the Management API
#   SEED_LABEL        label to put on any container it starts
#   SEED_IMAGE        the pinned Python image of the env (env/lib.sh), for hooks that run Python
# Exit 0 when the seed is in place. up.sh stops a hook after 600 s.
set -u -o pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# credentials as a read-only mounted file, so they are not in the container's config (docker inspect)
exec docker run --rm --network "$SEED_NETWORK" --label "$SEED_LABEL" \
  -v "$SEED_RESOLV:/etc/resolv.conf:ro" -v "$HERE/seed.py:/seed.py:ro" -v "$SEED_CREDENTIALS:/run/secrets/seed.env:ro" \
  "$SEED_IMAGE" python -u /seed.py "$SEED_MGMT_URL" /run/secrets/seed.env
