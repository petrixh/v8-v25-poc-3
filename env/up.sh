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

# Start one copy of the app (env/README.md): its own internal network, logging DNS, MySQL, RabbitMQ, mgmt-server,
# the seed, vv8-ui as $APP_ENV_CONTAINER, and an inbound-only forward to 127.0.0.1:$APP_ENV_PORT.
# Returns 0 once http://127.0.0.1:$APP_ENV_PORT/UI/login answers 200.
#   up.sh             build the app image for HEAD if it does not exist yet, then start
#   up.sh --no-build  the image for HEAD must exist
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

BUILD=1
for a in "$@"; do case "$a" in --no-build) BUILD=0 ;; *) echo "usage: $0 [--no-build]" >&2; exit 2 ;; esac; done
need_host_tools
check_user_files

# ---------------------------------------------------------------- image
build_image() {
  local tag commit short b log settings m2 hosts bad
  commit="$(app_commit)" || die "not a git checkout: $REPO"
  short="$(echo "$commit" | cut -c1-12)"; tag="$(image_tag)"
  mkdir -p "$STATE_ROOT/build" "$LOG_ROOT" || die "state dir $STATE_ROOT"
  # copies started together build once: the first holds the lock, the others wait and then find the image.
  # Without flock there is no lock: concurrent builds of one commit each build (same result, more time).
  exec 9>"$STATE_ROOT/build.lock" || die "build lock file"
  if command -v flock >/dev/null; then flock -w 1800 9 || die "build lock $STATE_ROOT/build.lock still held after 1800 s"
  else say "note: no flock on this host: no build lock (README.md \"Needs\")"; fi
  if have_image "$tag" && [ "${APP_ENV_REBUILD:-0}" != 1 ]; then say "image $tag exists (APP_ENV_REBUILD=1 rebuilds it)"; return 0; fi
  [ -z "$(git -C "$REPO" status --porcelain)" ] || say "note: uncommitted changes are not in the image; it is built from HEAD ($short)"
  b="$(mktemp -d "$STATE_ROOT/build/$short.XXXXXX")" || die "build dir"; BUILD_DIR="$b"   # own dir per build, removed on exit
  log="$LOG_ROOT/build-$short-$(date -u +%Y%m%dT%H%M%SZ).log"
  settings="${APP_ENV_MAVEN_SETTINGS:-$ENV_DIR/maven-settings.xml}"; m2="${APP_ENV_MAVEN_REPO:-$HOME/.m2/repository}"
  hosts="${APP_ENV_MAVEN_HOSTS:-repo.maven.apache.org maven.vaadin.com}"
  [ -r "$settings" ] || die "Maven settings $settings not readable"
  mkdir -p "$b/src" "$b/ctx" "$m2" || die "build dir $b"
  git -C "$REPO" archive "$commit" | tar -x -C "$b/src" || die "git archive $short"
  say "building $tag: Maven in $MAVEN_IMG, -pl hawkbit-vv8-ui,hawkbit-mgmt-server -am -DskipTests package (log $log)"
  local t0=$SECONDS mvn=(docker run --rm --user "$(id -u):$(id -g)" -e HOME=/tmp -e MAVEN_CONFIG=/tmp/.m2
    -v "$b/src:/src" -v "$m2:/m2" -v "$(cd "$(dirname "$settings")" && pwd)/$(basename "$settings"):/settings.xml:ro"
    "$MAVEN_IMG" mvn -B -s /settings.xml -Dmaven.repo.local=/m2 -DskipNexusStaging)
  if ! { "${mvn[@]}" -f /src/pom.xml -pl hawkbit-runtime/hawkbit-vv8-ui,hawkbit-runtime/hawkbit-mgmt-server -am -DskipTests package \
          && "${mvn[@]}" org.apache.maven.plugins:maven-dependency-plugin:3.6.1:copy -Dmdep.stripVersion=true \
             -Dartifact=org.mariadb.jdbc:mariadb-java-client:$MARIADB_VER -DoutputDirectory=/src/.env-drivers; } >"$log" 2>&1; then
    tail -30 "$log" >&2; die "Maven build failed (log $log)"
  fi
  # Maven may have downloaded only from the allowed hosts. This checks the log after the build; what prevents other
  # hosts is the settings file, which mirrors every repository to the allowed ones.
  bad="$(grep -oE 'Download(ing|ed) from [^:]+: https?://[^/]+' "$log" | sed -E 's#.*https?://##' | sort -u \
         | grep -vxF -f <(tr ' ' '\n' <<<"$hosts" | grep .) || true)"
  [ -z "$bad" ] || die "Maven downloaded from hosts outside APP_ENV_MAVEN_HOSTS: ${bad//$'\n'/ } (log $log)"
  local vv8="" mgmt="" f
  for f in "$b"/src/hawkbit-runtime/hawkbit-vv8-ui/target/hawkbit-vv8-ui-*.jar; do [[ "$f" =~ (sources|javadoc|original) ]] || vv8="$f"; done
  for f in "$b"/src/hawkbit-runtime/hawkbit-mgmt-server/target/hawkbit-mgmt-server-*.jar; do [[ "$f" =~ (sources|javadoc|original) ]] || mgmt="$f"; done
  [ -f "$vv8" ] && [ -f "$mgmt" ] && [ -f "$b/src/.env-drivers/mariadb-java-client.jar" ] || die "jars missing after the build (log $log)"
  mv "$vv8" "$b/ctx/vv8-ui.jar"; mv "$mgmt" "$b/ctx/mgmt-server.jar"; mv "$b/src/.env-drivers/mariadb-java-client.jar" "$b/ctx/"
  docker build -q -t "$tag" --build-arg JRE_IMAGE="$JRE_IMG" --build-arg APP_COMMIT="$commit" \
    -f "$ENV_DIR/docker/Dockerfile" "$b/ctx" >>"$log" 2>&1 || { tail -20 "$log" >&2; die "docker build failed (log $log)"; }
  rm -rf "$b"; BUILD_DIR=""
  say "image $tag built in $((SECONDS - t0)) s; Maven downloads: $(grep -c 'Downloaded from' "$log"), none outside: $hosts"
}

BUILD_DIR=""; trap '[ -z "$BUILD_DIR" ] || rm -rf "$BUILD_DIR"' EXIT
if [ "$BUILD" = 1 ]; then build_image; exec 9>&-   # release the build lock: copies start in parallel from here
else have_image "$(image_tag)" || die "--no-build, but image $(image_tag) does not exist"; fi

# ---------------------------------------------------------------- copy
[ -z "$(docker ps -aq --filter "label=$LABEL")$(docker network ls -q --filter "label=$LABEL")$(docker volume ls -q --filter "label=$LABEL")" ] \
  || die "copy $PROJECT already exists: run down.sh first"
exists "$APP_CONTAINER" && die "container $APP_CONTAINER exists and is not part of copy $PROJECT"
for v in "$V_MYSQL" "$V_RABBIT" "$V_ART"; do   # docker volume create would silently reuse it, with its old data
  docker volume inspect "$v" >/dev/null 2>&1 && die "volume $v exists and is not part of copy $PROJECT"
done
t_start=$SECONDS
{ rm -rf "$COPY_DIR" && mkdir -p "$COPY_DIR/dns" && chmod 700 "$COPY_DIR" \
  && image_tag > "$COPY_DIR/image" && echo "$PORT" > "$COPY_DIR/port"; } || die "state dir $COPY_DIR"
# mgmt-server's admin (the only Management API user): a fresh random password per copy, for the seed only.
# mgmt-server is reachable only on the internal network. Never printed. The properties file is mode 644 so that the
# container's uid 1000 can read it whatever the host uid; the copy dir (mode 700) keeps other host users out.
pw="$(head -c 24 /dev/urandom | base64 | tr -dc 'A-Za-z0-9')"
[ ${#pw} -ge 20 ] || die "could not generate a password"
{ ( umask 022; printf 'spring.security.user.password={noop}%s\n' "$pw" > "$COPY_DIR/mgmt-admin.properties" ) \
  && ( umask 077; printf 'MGMT_USER=admin\nMGMT_PASSWORD=%s\n' "$pw" > "$COPY_DIR/seed-credentials.env" ); } \
  || die "credential files in $COPY_DIR"
unset pw
{ : > "$COPY_DIR/dns/dns.log" && chmod 666 "$COPY_DIR/dns/dns.log"; } || die "DNS log in $COPY_DIR"

docker network create --internal --label "$LABEL" "$NET_INT" >/dev/null || die "network $NET_INT"
docker network create --label "$LABEL" "$NET_EDGE" >/dev/null || die "network $NET_EDGE"
for v in "$V_MYSQL" "$V_RABBIT" "$V_ART"; do docker volume create --label "$LABEL" "$v" >/dev/null || die "volume $v"; done

# logging DNS: answers only this copy's container names; everything else NXDOMAIN, logged
allow="mysql,rabbitmq,$C_MYSQL,$C_RABBIT,$C_MGMT,$APP_CONTAINER"
docker run -d --name "$C_DNS" --label "$LABEL" --network "$NET_INT" -e ALLOW="$allow" -e LOG=/log/dns.log \
  -v "$ENV_DIR/docker/dnslog.py:/dnslog.py:ro" -v "$COPY_DIR/dns:/log" "$PY_IMG" python -u /dnslog.py >/dev/null || die "dns"
dns_ip="$(docker inspect -f "{{(index .NetworkSettings.Networks \"$NET_INT\").IPAddress}}" "$C_DNS")"
[ -n "$dns_ip" ] || die "no IP for $C_DNS"
printf 'nameserver %s\noptions ndots:0 attempts:1 timeout:2\n' "$dns_ip" > "$COPY_DIR/resolv.conf" || die "resolv.conf"
say "network $NET_INT ($(docker network inspect -f '{{(index .IPAM.Config 0).Subnet}}' "$NET_INT"), internal), DNS $dns_ip"

docker run -d --name "$C_MYSQL" --hostname mysql --label "$LABEL" --network "$NET_INT" --network-alias mysql \
  -v "$V_MYSQL:/var/lib/mysql" -e MYSQL_DATABASE=hawkbit -e MYSQL_ALLOW_EMPTY_PASSWORD=true "$MYSQL_IMG" >/dev/null || die "mysql"
docker run -d --name "$C_RABBIT" --hostname rabbitmq --label "$LABEL" --network "$NET_INT" --network-alias rabbitmq \
  -v "$V_RABBIT:/var/lib/rabbitmq" -e RABBITMQ_DEFAULT_VHOST=/ -e RABBITMQ_DEFAULT_USER=guest -e RABBITMQ_DEFAULT_PASS=guest \
  "$RABBIT_IMG" >/dev/null || die "rabbitmq"
# TCP ping: the image's first-start init server listens on the socket only. A probe can hang (seen once with
# rabbitmq-diagnostics), so every try has its own time limit.
wait_for 180 mysql timeout 20 docker exec "$C_MYSQL" mysqladmin ping -h 127.0.0.1 --protocol=TCP --silent || die "mysql not ready"
wait_for 180 rabbitmq timeout 30 docker exec "$C_RABBIT" rabbitmq-diagnostics -q check_port_connectivity || die "rabbitmq not ready"

# mgmt-server creates the schema (Flyway), then the seed goes in through its Management API
create_mgmt || die "create mgmt-server"
create_ui || die "create vv8-ui"
timeout 120 docker start "$C_MGMT" >/dev/null || die "start mgmt-server"
wait_for 240 "mgmt-server (schema)" mgmt_up || { docker logs --tail 40 "$C_MGMT" >&2; die "mgmt-server did not start"; }
seed="${APP_ENV_SEED:-$ENV_DIR/seed/seed.sh}"
if [ "$seed" = none ]; then
  say "seed: none (APP_ENV_SEED=none): fresh schema only"
else
  say "seed: $seed"
  SEED_NETWORK="$NET_INT" SEED_RESOLV="$COPY_DIR/resolv.conf" SEED_MGMT_URL="http://$C_MGMT:8080" \
    SEED_CREDENTIALS="$COPY_DIR/seed-credentials.env" SEED_LABEL="$LABEL" SEED_IMAGE="$PY_IMG" \
    timeout 600 "$seed" || die "seed hook $seed failed or took over 600 s"
fi
# the seed state, which reset.sh restores: taken with mgmt-server stopped, so nothing writes during the dump
docker stop -t 20 "$C_MGMT" >/dev/null || die "stop mgmt-server before the seed dump"
running "$C_MGMT" && die "mgmt-server still running: no seed dump taken"
dump_db > "$COPY_DIR/seed.sql" || die "seed dump"
say "seed dump: $(grep -c '^INSERT' "$COPY_DIR/seed.sql") INSERT statements, sha256 $(sha256sum < "$COPY_DIR/seed.sql" | cut -c1-12)"
if have_user_files; then   # mgmt-server gets the test users too, from here on (the seed ran as admin without them)
  { docker rm "$C_MGMT" >/dev/null && create_mgmt --users; } || die "recreate mgmt-server with the test-user files"
  say "mgmt-server: recreated with the test-user files"
fi
delete_queues

# inbound-only forward 127.0.0.1:$PORT -> vv8-ui:8082
docker run -d --name "$C_PROXY" --label "$LABEL" --network "$NET_EDGE" -p "127.0.0.1:$PORT:8082" \
  -e TARGET_HOST="$APP_CONTAINER" -e TARGET_PORT=8082 -e LISTEN_PORT=8082 \
  -v "$ENV_DIR/docker/tcpproxy.py:/p.py:ro" "$PY_IMG" python -u /p.py >/dev/null || die "proxy (port $PORT taken?)"
docker network connect "$NET_INT" "$C_PROXY" || die "proxy network"

start_apps || { docker logs --tail 60 "$APP_CONTAINER" >&2; die "app did not start"; }
say "up in $((SECONDS - t_start)) s: http://127.0.0.1:$PORT/UI/ (container $APP_CONTAINER, image $(image_tag_in_use))"
