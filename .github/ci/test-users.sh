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

# Throw-away test users for one CI run (.github/CI.md). Writes, outside the checkout:
#   <dir>/test-users.properties  the users for vv8-ui (APP_ENV_USERS_PROPERTIES); passwords only as ${MIG_TU_*_PW} references
#   <dir>/test-users.env         the plain passwords as MIG_TU_*_PW (APP_ENV_USERS_ENV, and the smoke's passwordFile), mode
#                                600, readable by its owner and by the app's uid 1000 only (see grant_app_read)
# Same usernames and permissions as the migration's test users; every password is new and random per run, so CI needs no
# repository secret. Never prints a password; on GitHub Actions each one is masked in the log.
# Usage: .github/ci/test-users.sh [dir]   (default ~/.config/mig-ci)
set -euo pipefail

APP_UID=1000   # the app's user inside its container (env/docker/Dockerfile); env/ mounts both files there read-only

# The app reads the env file as uid 1000 (env/README.md "Needs"). On a GitHub-hosted runner the owner is uid 1001, so
# mode 600 alone locks the app out. Grant read to uid 1000 and nobody else: with a POSIX ACL (mode stays 600 for the
# owning group and others), or, without setfacl, by giving the file to group 1000 (mode 640, needs sudo). Never 644.
grant_app_read() {
  local f="$1"
  if [ "$(id -u)" = "$APP_UID" ]; then echo "mode 600 (owner is uid $APP_UID)"; return 0; fi
  if command -v setfacl >/dev/null && setfacl -m "u:$APP_UID:r" "$f" 2>/dev/null; then
    echo "mode 600 plus ACL user:$APP_UID:r"; return 0
  fi
  if sudo -n chgrp "$APP_UID" "$f" 2>/dev/null; then chmod 640 "$f"; echo "mode 640, group $APP_UID"; return 0; fi
  echo "cannot make $f readable for uid $APP_UID without making it world-readable (no setfacl, no sudo)" >&2; return 1
}

dir="${1:-$HOME/.config/mig-ci}"
mkdir -p "$dir"; chmod 700 "$dir"

# username|permissions|password variable
users=(
  "admin|ALL|MIG_TU_ADMIN_PW"
  "readonly|READ_TARGET,READ_REPOSITORY,READ_ROLLOUT|MIG_TU_READONLY_PW"
  "targets|READ_TARGET,CREATE_TARGET,UPDATE_TARGET,DELETE_TARGET|MIG_TU_TARGETS_PW"
  "repository|READ_REPOSITORY,CREATE_REPOSITORY,UPDATE_REPOSITORY,DELETE_REPOSITORY,DOWNLOAD_REPOSITORY_ARTIFACT|MIG_TU_REPOSITORY_PW"
)

props="$dir/test-users.properties"; envf="$dir/test-users.env"
( umask 077; : > "$envf" )
echo "# Throw-away CI test users (.github/ci/test-users.sh). Passwords come from the env file as \${MIG_TU_*_PW}." > "$props"
i=0
for u in "${users[@]}"; do
  IFS='|' read -r name perms var <<<"$u"
  pw="$(head -c 32 /dev/urandom | base64 | tr -dc 'A-Za-z0-9' | cut -c1-24)"
  [ "${#pw}" -ge 16 ] || { echo "could not generate a password" >&2; exit 1; }
  if [ -n "${GITHUB_ACTIONS:-}" ]; then echo "::add-mask::$pw"; fi   # masked in every later log line of the run
  printf '%s=%s\n' "$var" "$pw" >> "$envf"
  # shellcheck disable=SC2016  # a literal ${...} reference that Spring resolves from the env file
  printf 'hawkbit.server.im.users[%d].username=%s\nhawkbit.server.im.users[%d].password={noop}${%s}\nhawkbit.server.im.users[%d].permissions=%s\n' \
    "$i" "$name" "$i" "$var" "$i" "$perms" >> "$props"
  i=$((i + 1))
done
unset pw
# The properties file holds no password; the app container (another uid) must be able to read it.
chmod 644 "$props"
access="$(grant_app_read "$envf")"
echo "test users: ${#users[@]} written to $dir (passwords random, not printed); password file: $access"
