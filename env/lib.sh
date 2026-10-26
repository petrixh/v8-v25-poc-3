# Shared by up.sh, reset.sh and down.sh. Sourced, not run. See README.md in this folder.
# Needs bash, git, docker and curl on the host. Never prints passwords: credential files are passed to Docker by path.
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

# ---- images: official Docker Hub images only, pinned by digest (multi-arch index digests)
MAVEN_IMG="${APP_ENV_MAVEN_IMAGE:-maven:3.9-eclipse-temurin-17}"
JRE_IMG="eclipse-temurin:17-jre@sha256:e04fa72be13b257a59fdec6dc14af78c8f80f035d3c1ed4e03e45552a231d4c8"
MYSQL_IMG="mysql:8.0"
RABBIT_IMG="rabbitmq:3-management-alpine"
PY_IMG="python:3.9.6-slim"
MARIADB_VER="3.1.4"
IMAGE_REPO="${APP_ENV_IMAGE_REPO:-mig-old-hawkbit}"

say() { printf '%s [%s] %s\n' "$(date -u +%H:%M:%S)" "$PROJECT" "$*"; }
die() { say "ERROR $*" >&2; exit 1; }

app_commit() { git -C "$REPO" rev-parse HEAD; }
image_tag()  { echo "$IMAGE_REPO:$(app_commit | cut -c1-12)"; }
have_image() { docker image inspect "$1" >/dev/null 2>&1; }
exists()     { docker inspect "$1" >/dev/null 2>&1; }
running()    { [ "$(docker inspect -f '{{.State.Running}}' "$1" 2>/dev/null)" = true ]; }

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

# ---- the two app JVMs. Same flags as the project's docs run them: profile mysql, MariaDB driver on the loader path.
APP_ENV_ARGS=(-e SPRING_DATASOURCE_URL=jdbc:mariadb://mysql:3306/hawkbit -e SPRING_DATASOURCE_USERNAME=root
  -e SPRING_RABBITMQ_HOST=rabbitmq -e SPRING_RABBITMQ_USERNAME=guest -e SPRING_RABBITMQ_PASSWORD=guest)
JAVA_ARGS=(-Dspring.profiles.active=mysql -Xms768m -Xmx768m -Dloader.path=/drivers/ org.springframework.boot.loader.PropertiesLauncher)

create_mgmt() { # mgmt-server: Flyway schema owner, Management API (seed), rollout and auto-assign schedulers
  docker create --name "$C_MGMT" --hostname "$C_MGMT" --label "$LABEL" --network "$NET_INT" \
    -v "$COPY_DIR/resolv.conf:/etc/resolv.conf:ro" -v "$V_ART:/artifactrepo" \
    "${APP_ENV_ARGS[@]}" --env-file "$COPY_DIR/mgmt-admin.env" \
    "$(image_tag_in_use)" java -cp /app/mgmt-server.jar "${JAVA_ARGS[@]}" >/dev/null
}
create_ui() { # vv8-ui: the Vaadin 8 UI, the app under migration; test users only here (as the pre-check)
  local users=()
  if [ -n "${APP_ENV_USERS_ENV:-}" ]; then
    [ -r "$APP_ENV_USERS_ENV" ] || die "APP_ENV_USERS_ENV is set but not readable"
    users+=(--env-file "$APP_ENV_USERS_ENV")
  fi
  local extra=()
  if [ -n "${APP_ENV_USERS_PROPERTIES:-}" ]; then
    [ -r "$APP_ENV_USERS_PROPERTIES" ] || die "APP_ENV_USERS_PROPERTIES is set but not readable"
    users+=(-v "$(cd "$(dirname "$APP_ENV_USERS_PROPERTIES")" && pwd)/$(basename "$APP_ENV_USERS_PROPERTIES"):/run/users/users.properties:ro")
    extra+=(--spring.config.additional-location=file:/run/users/users.properties)
  fi
  docker create --name "$APP_CONTAINER" --hostname "$APP_CONTAINER" --label "$LABEL" --network "$NET_INT" \
    -v "$COPY_DIR/resolv.conf:/etc/resolv.conf:ro" -v "$V_ART:/artifactrepo" \
    "${APP_ENV_ARGS[@]}" "${users[@]}" \
    "$(image_tag_in_use)" java -cp /app/vv8-ui.jar "${JAVA_ARGS[@]}" "${extra[@]}" >/dev/null
}
image_tag_in_use() { cat "$COPY_DIR/image" 2>/dev/null || image_tag; }

start_apps() { # start mgmt-server and vv8-ui (already created), wait for both
  docker start "$C_MGMT" "$APP_CONTAINER" >/dev/null || return 1
  wait_for 240 "mgmt-server" mgmt_up || return 1
  wait_for 240 "vv8-ui at http://127.0.0.1:$PORT/UI/login" ui_up
}

# ---- database and broker state
mysql_exec() { docker exec -i "$C_MYSQL" mysql -uroot "$@"; }
dump_db() { # the whole hawkbit schema, deterministic: no dump date, rows in primary key order
  docker exec "$C_MYSQL" mysqldump -uroot --single-transaction --skip-dump-date --order-by-primary --routines --events hawkbit
}
delete_queues() { # RabbitMQ: drop every queue of vhost /; the apps declare theirs again when they start
  local q
  timeout 60 docker exec "$C_RABBIT" rabbitmqctl list_queues -q --no-table-headers -p / name 2>/dev/null | while read -r q; do
    [ -n "$q" ] && timeout 60 docker exec "$C_RABBIT" rabbitmqctl delete_queue -p / "$q" >/dev/null 2>&1
  done
  return 0
}
clear_artifacts() { # the artifact store both JVMs share; the seed has no artifacts
  docker run --rm --network none --label "$LABEL" -v "$V_ART:/artifactrepo" --entrypoint sh "$(image_tag_in_use)" \
    -c 'find /artifactrepo -mindepth 1 -delete' >/dev/null
}
