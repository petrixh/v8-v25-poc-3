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

# Replace every test-user password with *** in the files under the given paths, before they are uploaded
# (.github/CI.md "Job e2e"). ::add-mask:: masks the live log only, not files. The passwords are the values of the
# KEY=value env file (.github/ci/test-users.sh); they are never printed. A file over 50 MB is deleted unread,
# so it is not uploaded. Prints how many files were changed; exits non-zero when it cannot do its job, so the upload
# step (which requires this one to succeed) uploads nothing.
# Usage: .github/ci/redact.sh <test-users env file> <path>...
set -u -o pipefail
envfile="${1:?usage: redact.sh <env file> <path>...}"; shift
[ -r "$envfile" ] || { echo "redact: env file not readable" >&2; exit 1; }
command -v python3 >/dev/null || { echo "redact: needs python3" >&2; exit 1; }
python3 -I - "$envfile" "$@" <<'EOF'
import os, re, sys
values = []
for line in open(sys.argv[1], encoding="utf-8"):
    m = re.match(r"^\s*[A-Za-z_][A-Za-z0-9_]*=(.*?)\s*$", line)
    if m and m.group(1):
        values.append(m.group(1).encode())
if not values:
    sys.exit("redact: no values in the env file")
changed = big = 0
for root in sys.argv[2:]:
    for d, _, files in os.walk(root) if os.path.isdir(root) else [(os.path.dirname(root), [], [os.path.basename(root)])]:
        for f in files:
            p = os.path.join(d, f)
            if not os.path.isfile(p) or os.path.islink(p):
                continue
            if os.path.getsize(p) > 50 * 1024 * 1024:
                big += 1
                os.remove(p)  # too big to check: not uploaded
                continue
            data = open(p, "rb").read()
            new = data
            for v in values:
                new = new.replace(v, b"***")
            if new != data:
                open(p, "wb").write(new)
                changed += 1
print(f"redact: {changed} file(s) had a test-user password, now ***; {big} file(s) over 50 MB removed")
EOF
