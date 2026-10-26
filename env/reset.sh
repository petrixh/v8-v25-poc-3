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

# Return one running copy to its seed state (env/README.md "Reset"): stop both app JVMs, restore the seed dump,
# drop every RabbitMQ queue, empty the artifact store, start the JVMs again. Exit 0 when the app answers again.
# The JVM restart is deliberate: the app caches tenant configuration and rollout status in memory, which a
# database restore alone would leave stale.
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

[ -s "$COPY_DIR/seed.sql" ] || die "no seed dump for copy $PROJECT ($COPY_DIR/seed.sql): is it up?"
{ running "$C_MYSQL" && running "$C_RABBIT"; } || die "copy $PROJECT is not running (mysql/rabbitmq)"
t0=$SECONDS
docker stop -t 20 "$C_MGMT" "$APP_CONTAINER" >/dev/null || die "stop app containers"
mysql_exec -e 'DROP DATABASE IF EXISTS hawkbit; CREATE DATABASE hawkbit;' || die "recreate database"
mysql_exec hawkbit < "$COPY_DIR/seed.sql" || die "restore seed dump"
delete_queues
clear_artifacts || die "clear artifact store"
say "seed state restored in $((SECONDS - t0)) s (database, queues, artifacts); starting the app"
start_apps || { docker logs --tail 60 "$APP_CONTAINER" >&2; die "app did not start after reset"; }
say "reset done in $((SECONDS - t0)) s"
