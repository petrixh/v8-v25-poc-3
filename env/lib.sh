#
# Copyright (c) 2023 Contributors to the Eclipse Foundation
#
# This program and the accompanying materials are made
# available under the terms of the Eclipse Public License 2.0
# which is available at https://www.eclipse.org/legal/epl-2.0/
#
# SPDX-License-Identifier: EPL-2.0
#

# Shared by up.sh, reset.sh and down.sh. Sourced, not run. See README.md in this folder.
# Host prerequisites: README.md "Needs" (need_host_tools checks them). Never prints passwords: credentials reach the
# containers as read-only mounted files, never as environment variables.
# shellcheck disable=SC2034  # names used by the scripts that source this file
set -u -o pipefail

ENV_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO="$(cd "$ENV_DIR/.." && pwd)"

# ---- one copy: names come from the replay runner's contract (tools/playwright/replay/README.md "The env scripts")
PROJECT="${APP_ENV_PROJECT:-hawkbit-env}"
PORT="${APP_ENV_PORT:-18081}"
APP_CONTAINER="${APP_ENV_CONTAINER:-$PROJECT-app}"
[[ "$PROJECT" =~ ^[a-z0-9][a-z0-9_.-]*$ ]] || { echo "APP_ENV_PROJECT must match ^[a-z0-9][a-z0-9_.-]*\$" >&2; exit 2; }
[[ "$APP_CONTAINER" =~ ^[a-zA-Z0-9][a-zA-Z0-9_.-]*$ ]] || { echo "APP_ENV_CONTAINER is not a valid container name" >&2; exit 2; }
[[ "$PORT" =~ ^[0-9]+$ ]] || { echo "APP_ENV_PORT must be a port number" >&2; exit 2; }

LABEL="mig.env.project=$PROJECT"          # every container, network and volume of this copy carries it
NET_INT="$PROJECT-int"                    # --internal: no route out; the app containers live only here
NET_EDGE="$PROJECT-edge"                  # the inbound proxy's published port
C_DNS="$PROJECT-dns"; C_MYSQL="$PROJECT-mysql"; C_RABBIT="$PROJECT-rabbitmq"; C_MGMT="$PROJECT-mgmt"; C_PROXY="$PROJECT-proxy"
V_MYSQL="$PROJECT-mysql-data"; V_RABBIT="$PROJECT-rabbitmq-data"; V_ART="$PROJECT-artifacts"

STATE_ROOT="${APP_ENV_STATE_DIR:-$ENV_DIR/.state}"
COPY_DIR="$STATE_ROOT/copies/$PROJECT"    # resolv.conf, DNS log, seed dump, mgmt-server credential; removed by down.sh
LOG_ROOT="${APP_ENV_LOG_DIR:-$STATE_ROOT/logs}"

# ---- images: official Docker Hub images only, each pinned by its multi-arch index digest, so every host runs the
# same versions: Maven 3.9.16 on JDK 17.0.20.1, JRE 17.0.20.1, MySQL 8.0.46, RabbitMQ 3.13.7, Python 3.9.6 (helpers
# only: DNS, proxy, seed). The tag is for readers; Docker resolves the digest. Change a version here and in README.md.
MAVEN_IMG="${APP_ENV_MAVEN_IMAGE:-maven:3.9-eclipse-temurin-17@sha256:1a352420f7aba21f5ad08df31bab55f74c013fb491f1ae8ab1dd7ff9ed698584}"
JRE_IMG="eclipse-temurin:17-jre@sha256:e04fa72be13b257a59fdec6dc14af78c8f80f035d3c1ed4e03e45552a231d4c8"
MYSQL_IMG="mysql:8.0@sha256:7dcddc01f13bab2f15cde676d44d01f61fc9f99fe7785e86196dfc07d358ae2b"
RABBIT_IMG="rabbitmq:3-management-alpine@sha256:606d8c0d6b3c18d1da9afc53bc7cdb2a8d5486df91b5a9830e9e07626c9ae281"
PY_IMG="python:3.9.6-slim@sha256:4115592fd02679fb3d9e8c513cae33ad3fdd64747b64d32b504419d7118bcd7c"
MARIADB_VER="3.1.4"
IMAGE_REPO="${APP_ENV_IMAGE_REPO:-mig-old-hawkbit}"

say() { printf '%s [%s] %s\n' "$(date -u +%H:%M:%S)" "$PROJECT" "$*"; }
die() { say "ERROR $*" >&2; exit 1; }

app_commit() { git -C "$REPO" rev-parse HEAD; }
image_tag()  { echo "$IMAGE_REPO:$(app_commit | cut -c1-12)"; }
have_image() { docker image inspect "$1" >/dev/null 2>&1; }
exists()     { docker inspect "$1" >/dev/null 2>&1; }
running()    { [ "$(docker inspect -f '{{.State.Running}}' "$1" 2>/dev/null)" = true ]; }

need_host_tools() { # README.md "Needs": bash 4.4+ (empty arrays under set -u), GNU coreutils timeout and sha256sum
  ((BASH_VERSINFO[0] > 4 || (BASH_VERSINFO[0] == 4 && BASH_VERSINFO[1] >= 4))) || die "needs bash 4.4 or later (this is $BASH_VERSION)"
  local t; for t in docker git curl timeout sha256sum; do command -v "$t" >/dev/null || die "needs '$t' on the host (README.md \"Needs\")"; done
}

wait_for() { # wait_for <seconds> <what> <command...>; each try of the command needs its own time limit (see callers)
  local max="$1" what="$2" t0=$SECONDS; shift 2
  until "$@" >/dev/null 2>&1; do
    [ $((SECONDS - t0)) -ge "$max" ] && { say "ERROR $what not ready after ${max}s"; return 1; }
    sleep 2
  done
  say "$what ready after $((SECONDS - t0)) s"
}

# ---- readiness
mgmt_up() { [ "$(timeout 15 docker exec "$C_MGMT" curl -s -o /dev/null -w '%{http_code}' --max-time 5 http://localhost:8080/rest/v1/targets 2>/dev/null)" = 401 ]; }
ui_up()   { [ "$(curl -s -o /dev/null -w '%{http_code}' --max-time 5 "http://127.0.0.1:$PORT/UI/login")" = 200 ]; }

# ---- the two app JVMs, with the settings the migration's pre-check ran them with: profile mysql, MariaDB driver on the
# loader path. (The project's own Dockerfile-mysql puts the driver in BOOT-INF/lib and sets more JVM flags; not used.)
APP_ENV_ARGS=(-e SPRING_DATASOURCE_URL=jdbc:mariadb://mysql:3306/hawkbit -e SPRING_DATASOURCE_USERNAME=root
  -e SPRING_RABBITMQ_HOST=rabbitmq -e SPRING_RABBITMQ_USERNAME=guest -e SPRING_RABBITMQ_PASSWORD=guest)
JAVA_ARGS=(-Dspring.profiles.active=mysql -Xms768m -Xmx768m -Dloader.path=/drivers/ org.springframework.boot.loader.PropertiesLauncher)
abs_path() { echo "$(cd "$(dirname "$1")" && pwd)/$(basename "$1")"; }

create_mgmt() { # mgmt-server: Flyway schema owner, Management API (seed), rollout and auto-assign schedulers
  # its admin password comes from a mounted properties file, so it is not in the container's config (docker inspect)
  docker create --name "$C_MGMT" --hostname "$C_MGMT" --label "$LABEL" --network "$NET_INT" \
    -v "$COPY_DIR/resolv.conf:/etc/resolv.conf:ro" -v "$V_ART:/artifactrepo" \
    -v "$COPY_DIR/mgmt-admin.properties:/run/secrets/mgmt-admin.properties:ro" "${APP_ENV_ARGS[@]}" \
    "$(image_tag_in_use)" java -cp /app/mgmt-server.jar "${JAVA_ARGS[@]}" \
    --spring.config.additional-location=file:/run/secrets/mgmt-admin.properties >/dev/null
}
check_user_files() { # up.sh, before it starts anything. The env file is read by Spring as properties: KEY=value
  # lines, values without '\' or leading blanks, so that both formats give the same values. Values are never shown.
  local bad
  if [ -n "${APP_ENV_USERS_PROPERTIES:-}" ]; then
    [ -r "$APP_ENV_USERS_PROPERTIES" ] || die "APP_ENV_USERS_PROPERTIES is set but not readable"
  fi
  if [ -n "${APP_ENV_USERS_ENV:-}" ]; then
    [ -r "$APP_ENV_USERS_ENV" ] || die "APP_ENV_USERS_ENV is set but not readable"
    bad="$(grep -nvE '^[[:space:]]*(#.*)?$|^[A-Za-z_][A-Za-z0-9_]*=([^[:space:]\\][^\\]*)?$' "$APP_ENV_USERS_ENV" | cut -d: -f1 | tr '\n' ' ')"
    [ -z "$bad" ] || die "APP_ENV_USERS_ENV: line(s) $bad not KEY=value, or the value has a backslash or leading blank (values not shown)"
  fi
  return 0
}
create_ui() { # vv8-ui: the Vaadin 8 UI, the app under migration; test users only here (as the pre-check)
  # Both test-user files (checked by check_user_files) are mounted read-only and loaded as Spring config, so no password is in the container's
  # config. The env file's KEY=value lines are read as properties: they resolve ${KEY} in the properties file.
  local mounts=() locs=()
  if [ -n "${APP_ENV_USERS_PROPERTIES:-}" ]; then
    mounts+=(-v "$(abs_path "$APP_ENV_USERS_PROPERTIES"):/run/users/users.properties:ro"); locs+=(file:/run/users/users.properties)
  fi
  if [ -n "${APP_ENV_USERS_ENV:-}" ]; then
    mounts+=(-v "$(abs_path "$APP_ENV_USERS_ENV"):/run/users/users-env.properties:ro"); locs+=(file:/run/users/users-env.properties)
  fi
  local extra=()
  [ ${#locs[@]} -eq 0 ] || extra+=("--spring.config.additional-location=$(IFS=,; echo "${locs[*]}")")
  docker create --name "$APP_CONTAINER" --hostname "$APP_CONTAINER" --label "$LABEL" --network "$NET_INT" \
    -v "$COPY_DIR/resolv.conf:/etc/resolv.conf:ro" -v "$V_ART:/artifactrepo" \
    "${APP_ENV_ARGS[@]}" "${mounts[@]}" \
    "$(image_tag_in_use)" java -cp /app/vv8-ui.jar "${JAVA_ARGS[@]}" "${extra[@]}" >/dev/null
}
image_tag_in_use() { cat "$COPY_DIR/image" 2>/dev/null || image_tag; }

start_apps() { # start mgmt-server and vv8-ui (already created), wait for both
  timeout 120 docker start "$C_MGMT" "$APP_CONTAINER" >/dev/null || return 1
  wait_for 240 "mgmt-server" mgmt_up || return 1
  wait_for 240 "vv8-ui at http://127.0.0.1:$PORT/UI/login" ui_up
}

# ---- database and broker state
mysql_exec() { timeout 300 docker exec -i "$C_MYSQL" mysql -uroot "$@"; }
dump_db() { # the whole hawkbit schema, deterministic: no dump date, rows in primary key order
  timeout 300 docker exec "$C_MYSQL" mysqldump -uroot --single-transaction --skip-dump-date --order-by-primary --routines --events hawkbit
}
delete_queues() { # RabbitMQ: drop every queue of vhost /; the apps declare theirs again when they start
  local q
  timeout 60 docker exec "$C_RABBIT" rabbitmqctl list_queues -q --no-table-headers -p / name 2>/dev/null | while read -r q; do
    [ -n "$q" ] && timeout 60 docker exec "$C_RABBIT" rabbitmqctl delete_queue -p / "$q" >/dev/null 2>&1
  done
  return 0
}
clear_artifacts() { # the artifact store both JVMs share; the seed has no artifacts
  timeout 120 docker run --rm --network none --label "$LABEL" -v "$V_ART:/artifactrepo" --entrypoint sh "$(image_tag_in_use)" \
    -c 'find /artifactrepo -mindepth 1 -delete' >/dev/null
}
