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

# The CI job summary (.github/CI.md) as a Markdown table on stdout, from what the earlier steps left in $CI_OUT and
# $APP_ENV_LOG_DIR. Every row is optional: a missing or partial file gives "not run" or no row, never an error.
# Always exits 0, so a red run still gets its summary.
# Usage: CI_OUT=... APP_ENV_LOG_DIR=... APP_ENV_PROJECT=... .github/ci/summary.sh
set +e +o pipefail
out="${CI_OUT:-$HOME/ci-out}"; logs="${APP_ENV_LOG_DIR:-$out/env-logs}"; project="${APP_ENV_PROJECT:-hawkbit-env}"

newest() { # newest <glob...>: the most recently modified existing path, or nothing
  local f best=""
  for f in "$@"; do [ -e "$f" ] || continue; { [ -z "$best" ] || [ "$f" -nt "$best" ]; } && best="$f"; done
  printf '%s' "$best"
}
hosts_of() { # hosts_of <maven log>: "host count; ..." of the downloads, then the total
  grep -oE 'Downloaded from [^:]+: https?://[^/]+' "$1" 2>/dev/null | sed -E 's#.*https?://##' | sort | uniq -c \
    | awk '{printf "%s %s; ", $2, $1}'
  local n; n="$(grep -c 'Downloaded from' "$1" 2>/dev/null)"; printf '%s downloads' "${n:-0}"
}
row() { printf '| %s | %s |\n' "$1" "$2"; }

sha="${GITHUB_SHA:-$(git rev-parse HEAD 2>/dev/null)}"; echo "## CI of ${sha:0:12}"
echo
row Check Result
row --- ---

b="$(newest "$logs"/build-*.log)"
if [ -n "$b" ]; then row "Maven hosts (image build)" "$(hosts_of "$b")"; else row "Image build" "no build log (not run, or the image existed)"; fi
if [ -f "$out/up.log" ]; then
  grep -E 'image .* built in|image .* exists|up in [0-9]+ s|ERROR' "$out/up.log" | sed -E 's/^[0-9:]+ \[[^]]*\] //' \
    | while IFS= read -r l; do row Env "$l"; done
else
  row Env "not run"
fi

if [ -s "$out/smoke/smoke.json" ]; then
  s="$(node -e '
    const fs=require("fs"); const r=JSON.parse(fs.readFileSync(process.argv[1],"utf8")); let h={};
    try { for (const x of JSON.parse(fs.readFileSync(process.argv[2],"utf8")).blocked) {
      try { const u=new URL(x.url.replace(/^connect:\/\//,"http://")); h[u.host]=(h[u.host]||0)+1 } catch(e) {} } } catch(e) {}
    const p=r.filter(x=>x.pass).length;
    console.log((r.length ? p+" of "+r.length+" view checks pass" : "failed before the first view check (login?)")+"; blocked hosts: "+JSON.stringify(h));
  ' "$out/smoke/smoke.json" "$out/smoke/blocked-requests.json" 2>/dev/null)"
  row Smoke "${s:-smoke.json unreadable}"
else
  row Smoke "not run"
fi

if [ -s "$out/tests/counts.env" ]; then
  RUN='?' FAILURES='?' ERRORS='?' SKIPPED='?'
  # shellcheck disable=SC1091  # written by run-tests.sh: KEY=number lines only
  . "$out/tests/counts.env"
  row Tests "run $RUN, failures $FAILURES, errors $ERRORS, skipped $SKIPPED"
else
  row Tests "not run, or stopped before the test run"
fi
[ -f "$out/tests/tests-fetch.log" ] && row "Maven hosts (test fetch)" "$(hosts_of "$out/tests/tests-fetch.log")"

d="$(newest "$logs/$project"-*/)"
if [ -n "$d" ] && [ -f "$d/dns.log" ]; then
  row "DNS lookups in the copy" "$(awk '{print $3}' "$d/dns.log" | sort | uniq -c | awk '{printf "%s %s; ", $2, $1}')"
fi
exit 0
