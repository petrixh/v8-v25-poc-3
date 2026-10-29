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

# The test suite of the vv8-ui path (.github/CI.md), as the migration's pre-check runs it:
#   1. fetch (online, Maven mirrors of env/maven-settings.xml only): the surefire JUnit provider, which package
#      -DskipTests does not resolve and surefire fetches only when it runs a test: one pure unit test of hawkbit-core,
#      whose result is ignored here (step 2 runs it again and counts it)
#   2. tests, offline, in Docker on the internal network of a running env copy (env/up.sh), with its RabbitMQ:
#      -pl hawkbit-runtime/hawkbit-vv8-ui -am -fae verify
# RABBITMQ_TEST_HOSTNAME and RABBITMQ_SERVER_REQUIRED=true make the DMF integration tests fail instead of skipping
# silently when the broker is not reachable.
# Test classes run in alphabetical order. Surefire's default (filesystem) follows the directory order of
# target/test-classes, which differs between runners, and some tests depend on the order: on GitHub runners
# TargetManagementSearchTest failed with targets another test left behind, on some runs and not on others.
# Writes <out>/tests.log, <out>/tests-fetch.log and <out>/counts.env (RUN, FAILURES, ERRORS, SKIPPED).
# Usage: .github/ci/run-tests.sh <out dir>   with APP_ENV_PROJECT (and APP_ENV_STATE_DIR) of the running copy
# The copy's names, its network and the Maven image (pinned by digest) come from env/lib.sh, so the tests run on the
# same Maven and JDK as the image build, and with the same Node/npm home (BUILD_HOME) as its frontend build.
out="${1:?usage: run-tests.sh <out dir>}"; mkdir -p "$out"; out="$(cd "$out" && pwd)"
# shellcheck source=env/lib.sh  # path from the repo root, where shellcheck runs
source "$(dirname "${BASH_SOURCE[0]}")/../../env/lib.sh"   # sets REPO, PROJECT, COPY_DIR, NET_INT, MAVEN_IMG; set -u -o pipefail
resolv="$COPY_DIR/resolv.conf"
m2="${APP_ENV_MAVEN_REPO:-$HOME/.m2/repository}"
settings="${APP_ENV_MAVEN_SETTINGS:-$ENV_DIR/maven-settings.xml}"
hosts="${APP_ENV_MAVEN_HOSTS:-$MAVEN_HOSTS_DEFAULT}"
[ -r "$resolv" ] || { echo "no running copy $PROJECT ($resolv missing): start it with env/up.sh first" >&2; exit 2; }
mkdir -p "$m2" "$BUILD_HOME"

mvn() { # mvn <log> <docker args...> -- <maven args...>
  local log="$1"; shift; local dargs=()
  while [ "$1" != -- ]; do dargs+=("$1"); shift; done; shift
  docker run --rm --user "$(id -u):$(id -g)" "${BUILD_HOME_ARGS[@]}" -e MAVEN_CONFIG=/tmp/.m2 "${dargs[@]}" \
    -v "$REPO:$REPO" -w "$REPO" -v "$m2:/m2" -v "$settings:/settings.xml:ro" \
    "$MAVEN_IMG" mvn -B -s /settings.xml -Dmaven.repo.local=/m2 -DskipNexusStaging "$@" >"$log" 2>&1
}
check_hosts() { # the same rule as env/up.sh: Maven may download only from $hosts
  local bad
  bad="$(grep -oE 'Download(ing|ed) from [^:]+: https?://[^/]+|Downloading https?://[^/]+' "$1" | sed -E 's#.*https?://##' | sort -u \
         | grep -vxF -f <(tr ' ' '\n' <<<"$hosts" | grep .) || true)"
  [ -z "$bad" ] || { echo "Maven downloaded from hosts outside '$hosts': ${bad//$'\n'/ } ($1)" >&2; return 1; }
}

t0=$SECONDS
mvn "$out/tests-fetch.log" -- -pl hawkbit-core -Dtest=Base62UtilTest -Dmaven.test.failure.ignore=true test \
  || { tail -30 "$out/tests-fetch.log" >&2; echo "test fetch failed" >&2; exit 1; }
check_hosts "$out/tests-fetch.log" || exit 1
echo "fetch done in $((SECONDS - t0)) s; $(grep -c 'Downloaded from' "$out/tests-fetch.log") downloads"

t1=$SECONDS
mvn "$out/tests.log" --network "$NET_INT" -v "$resolv:/etc/resolv.conf:ro" \
  -e SPRING_RABBITMQ_HOST=rabbitmq -e RABBITMQ_TEST_HOSTNAME=rabbitmq -e RABBITMQ_TEST_ADMIN_URI=http://rabbitmq:15672/api/ \
  -e RABBITMQ_SERVER_REQUIRED=true \
  -- -o -pl hawkbit-runtime/hawkbit-vv8-ui -am -fae -Dspring.rabbitmq.host=rabbitmq \
  -Dsurefire.runOrder=alphabetical -Dfailsafe.runOrder=alphabetical verify
rc=$?
awk -F'[:,]' '/^\[(INFO|WARNING|ERROR)\] Tests run: [0-9]+, Failures: [0-9]+, Errors: [0-9]+, Skipped: [0-9]+$/ {r+=$2; f+=$4; e+=$6; s+=$8}
  END {printf "RUN=%d\nFAILURES=%d\nERRORS=%d\nSKIPPED=%d\n", r, f, e, s}' "$out/tests.log" > "$out/counts.env"
echo "tests finished in $((SECONDS - t1)) s, maven rc=$rc: $(tr '\n' ' ' < "$out/counts.env")"
grep -E 'Reactor Summary' -A40 "$out/tests.log" | grep -E 'SUCCESS|FAILURE|SKIPPED' | sed 's/^\[INFO\] //'
[ "$rc" -eq 0 ] || { grep -E '<<< (FAILURE|ERROR)!' "$out/tests.log" | head -40 >&2; exit 1; }
# A run that compiled but ran nothing is not green
[ "$(sed -n 's/^RUN=//p' "$out/counts.env")" -gt 0 ] || { echo "no tests ran" >&2; exit 1; }
