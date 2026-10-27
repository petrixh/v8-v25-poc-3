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

# Stop one copy and remove everything of it (env/README.md): every container, network and volume labelled
# mig.env.project=$APP_ENV_PROJECT, and its state dir. Touches nothing else. Safe after a half-finished up.sh and
# when the copy is already gone. Before removing, saves the DNS log and the app logs to $APP_ENV_LOG_DIR.
# Always exits 0; problems are reported on stderr.
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"
set +e

if ! docker version >/dev/null 2>&1; then say "WARN docker not reachable: nothing removed" >&2; exit 0; fi
containers="$(docker ps -aq --filter "label=$LABEL")"
networks="$(docker network ls -q --filter "label=$LABEL")"
volumes="$(docker volume ls -q --filter "label=$LABEL")"

if [ -n "$containers" ] || [ -d "$COPY_DIR" ]; then
  keep="$LOG_ROOT/$PROJECT-$(date -u +%Y%m%dT%H%M%SZ)"; mkdir -p "$keep"
  [ -f "$COPY_DIR/dns/dns.log" ] && cp "$COPY_DIR/dns/dns.log" "$keep/dns.log"
  for c in "$C_MGMT" "$APP_CONTAINER"; do
    [ "$(docker inspect -f '{{index .Config.Labels "mig.env.project"}}' "$c" 2>/dev/null)" = "$PROJECT" ] \
      && docker logs "$c" > "$keep/$c.log" 2>&1
  done
  say "logs saved to $keep"
fi
# shellcheck disable=SC2086
[ -n "$containers" ] && docker rm -f -v $containers >/dev/null
# shellcheck disable=SC2086
[ -n "$networks" ] && docker network rm $networks >/dev/null
# shellcheck disable=SC2086
[ -n "$volumes" ] && docker volume rm -f $volumes >/dev/null
rm -rf "$COPY_DIR"

left="$(docker ps -aq --filter "label=$LABEL")$(docker network ls -q --filter "label=$LABEL")$(docker volume ls -q --filter "label=$LABEL")"
if [ -n "$left" ]; then say "WARN left over of copy $PROJECT: ${left//$'\n'/ }" >&2
else say "down: $(wc -w <<<"$containers") containers, $(wc -w <<<"$networks") networks, $(wc -w <<<"$volumes") volumes removed; nothing of $PROJECT left"; fi
exit 0
