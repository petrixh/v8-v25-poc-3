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

# End-to-end tests of the UI (README.md "End-to-end tests"): start one copy of the app with up.sh, run
# mvn -Pe2e verify of hawkbit-runtime/hawkbit-vv8-ui-e2e against it on this host (JDK 21 and Maven 3.9 needed), then
# remove the copy with down.sh. The tests reset the copy (reset.sh) before each test class.
#   e2e.sh [--no-build] [--running] [-- <maven args>]
#     --no-build  the app image for HEAD must exist (up.sh --no-build)
#     --running   use the copy that is already up (APP_ENV_PROJECT, APP_ENV_PORT); do not start or remove it
#     <maven args> passed on, e.g. -- -Dit.test=Wf01LoginLogoutIT
# Settings: the APP_ENV_* of README.md (copy name, port, test-user files, Maven repository and settings); MVN (default
# mvn); PLAYWRIGHT_BROWSERS_PATH (Playwright's browser cache, default ~/.cache/ms-playwright on Linux).
# Exit: Maven's exit code (non-zero when a test failed or the copy did not start). Log: $APP_ENV_LOG_DIR/e2e-*.log.
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

UP_ARGS=(); RUNNING=0; MVN_ARGS=()
while [ $# -gt 0 ]; do
  case "$1" in
    --no-build) UP_ARGS+=(--no-build) ;;
    --running) RUNNING=1 ;;
    --) shift; MVN_ARGS=("$@"); break ;;
    *) echo "usage: $0 [--no-build] [--running] [-- <maven args>]" >&2; exit 2 ;;
  esac
  shift
done
MVN="${MVN:-mvn}"
command -v "$MVN" >/dev/null || die "needs Maven ($MVN) and JDK 21 on the host (README.md \"End-to-end tests\")"
settings="${APP_ENV_MAVEN_SETTINGS:-$ENV_DIR/maven-settings.xml}"; m2="${APP_ENV_MAVEN_REPO:-$HOME/.m2/repository}"
# Maven: the hosts the image build may use (only Maven Central is needed here). Playwright's browser download: its CDN
# (cdn.playwright.dev, observed) and the mirror Playwright falls back to.
hosts="${APP_ENV_MAVEN_HOSTS:-$MAVEN_HOSTS_DEFAULT}"
browser_hosts="${E2E_BROWSER_HOSTS:-cdn.playwright.dev playwright.download.prss.microsoft.com}"

if [ "$RUNNING" = 0 ]; then
  trap '"$ENV_DIR/down.sh"' EXIT
  "$ENV_DIR/up.sh" "${UP_ARGS[@]}" || die "the copy did not start"
else
  ui_up || die "no copy answers at http://127.0.0.1:$PORT/UI/login (--running)"
fi

mkdir -p "$LOG_ROOT" "$m2" || die "log dir $LOG_ROOT"
log="$LOG_ROOT/e2e-$(app_commit | cut -c1-12)-$(date -u +%Y%m%dT%H%M%SZ).log"
args=(-B -s "$settings" -Dmaven.repo.local="$m2" -DskipNexusStaging -Pe2e -pl hawkbit-runtime/hawkbit-vv8-ui-e2e
  "-De2e.baseUrl=http://127.0.0.1:$PORT" "-De2e.reset=$ENV_DIR/reset.sh")
[ -z "${APP_ENV_USERS_ENV:-}" ] || args+=("-De2e.usersEnv=$(abs_path "$APP_ENV_USERS_ENV")")
say "end-to-end tests against http://127.0.0.1:$PORT (log $log)"
t0=$SECONDS
(cd "$REPO" && "$MVN" "${args[@]}" "${MVN_ARGS[@]}" verify) 2>&1 | tee "$log"
rc=${PIPESTATUS[0]}
say "end-to-end tests finished in $((SECONDS - t0)) s, maven rc=$rc"

# Downloads only from the allowed hosts (as up.sh checks the image build): Maven's transfers and Playwright's browser
bad="$( { grep -oE 'Download(ing|ed) from [^:]+: https?://[^/]+' "$log" | sed -E 's#.*https?://##' | grep -vxF -f <(tr ' ' '\n' <<<"$hosts" | grep .)
          grep -E '^Downloading .* from https?://' "$log" | grep -oE 'https?://[^/]+' | sed -E 's#https?://##' | grep -vxF -f <(tr ' ' '\n' <<<"$browser_hosts" | grep .)
        } | sort -u)"
[ -z "$bad" ] || { say "ERROR downloads from hosts not allowed: ${bad//$'\n'/ } (log $log)" >&2; [ "$rc" -ne 0 ] || rc=1; }
exit "$rc"
