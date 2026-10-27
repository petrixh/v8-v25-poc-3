# CI

One GitHub Actions workflow, `.github/workflows/ci.yml`, with one job on a GitHub-hosted `ubuntu-24.04` runner. It builds the UI path (Java 21, Vaadin 25), starts one isolated copy of the app with the env scripts (`env/README.md`), logs in every test user and opens their views, and runs the test suite.

## When it runs

- Push to `main`, `mig-dev`, `stage/**`, `stabilise/**`, `fix/**`.
- Pull requests to `main` and `mig-dev`.
- Manually: Actions, CI, "Run workflow" (`workflow_dispatch`).

No schedule. A newer run on the same ref cancels the one still running. Time limits: 20 minutes for build and start, 5 for the Playwright install, 10 for the smoke, 20 for the tests, 60 for the job. A step over its limit fails, so its diagnostics are uploaded.

## Steps

| Step | Does | Fails when |
|---|---|---|
| Restore Maven repository, Node and npm cache, Playwright browser | `actions/cache` of `~/.m2/repository` and `~/.cache/mig-env-build-home` (Vaadin's Node and the npm cache; keys: hash of every `pom.xml`) and `~/.cache/ms-playwright` (key: Playwright version) | never |
| Test users | `.github/ci/test-users.sh`: four throw-away users with new random passwords (see "Test users") | |
| Build and start | `env/up.sh`. Builds the app image from the commit: Maven 3.9.16 / JDK 21.0.12.1 in Docker (the image `env/lib.sh` pins by digest), `-pl hawkbit-runtime/hawkbit-vv8-ui,hawkbit-runtime/hawkbit-mgmt-server -am -DskipTests package` with `env/maven-settings.xml` and `-DskipNexusStaging`. Then starts copy `ci` on an internal Docker network with MySQL 8.0 and RabbitMQ 3, seeds it, and serves the UI on `127.0.0.1:18081` | the build fails; Maven or the frontend build downloaded from a host other than `repo.maven.apache.org`, `nodejs.org` and `registry.npmjs.org` (the check is in `env/up.sh`; it sees Maven's transfers and the Node download, not npm's); the app does not start |
| Install Playwright | `npm install playwright@1.63.0` outside the checkout, then the Chromium headless shell | |
| Smoke | `.github/ci/smoke/smoke.cjs` with `.github/ci/smoke/new-app.json`: each test user logs in, opens `/UI/` and each view the user's permissions show by its route (e.g. `/UI/deployment`), then each of them from the menu (36 view checks). The browser may reach only the app's host; every other request is refused and listed | a login fails, a view has a console error, an HTTP status of 400 or more, a collapsed layout, or leaves the app |
| Tests | `.github/ci/run-tests.sh`. First a fetch (online, mirrors only) of the surefire JUnit provider, by running one unit test of `hawkbit-core` (`Base62UtilTest`), then `-pl hawkbit-runtime/hawkbit-vv8-ui -am -fae verify` offline in Docker on the copy's internal network, in the same pinned Maven image (taken from `env/lib.sh`), with its RabbitMQ (`RABBITMQ_TEST_HOSTNAME=rabbitmq`, `RABBITMQ_SERVER_REQUIRED=true`), test classes in alphabetical order (`-Dsurefire.runOrder=alphabetical`): the default order follows the file system and changes between runners, and some tests depend on it. Runs even when the smoke failed | a test fails; Maven downloaded from another host; no test ran; the broker is unreachable (the 89 DMF integration tests would otherwise skip silently) |
| Stop | `env/down.sh`, always. Saves the app and DNS logs to `~/ci-out/env-logs/` (`APP_ENV_LOG_DIR`), removes the copy | never |
| Summary | `.github/ci/summary.sh`: writes the job summary (and the same lines to the log): Maven download hosts, build and start times, smoke result and blocked hosts, test counts, the DNS names the copy looked up. A row whose input is missing says "not run" or is left out | never (the script always exits 0; the step also has `continue-on-error`) |
| Upload diagnostics | Only when a step failed or ran over its time limit (not when a newer push cancelled the run): `~/ci-out/` (step output, Maven build log, app and DNS logs, smoke screenshots and results, test logs), surefire and failsafe `*.txt` reports. Retention 3 days, `continue-on-error` | never |

The step "Build and start" is the deployable artefact per commit: the image `mig-new-hawkbit:<commit>` with both jars, built and run in the same job. It is not kept after the run.

## State on the new app

Stage 1a ticket 1a-05 moved the build and run images to Java 21; ticket 1a-06 (green build) switched the smoke to the new UI's selectors and routes (`.github/ci/smoke/new-app.json`). Not yet run on GitHub (remote writes are off). Known state, from the same checks run locally on the stage branch with the test-sources tickets merged:

- The build needs the ported test sources: `-DskipTests` still compiles them, so "Build and start" stays red until the test tickets are merged.
- Smoke: `admin` passes all 13 checks; `readonly`, `targets` and `repository` fail on Deployment, Rollout and Distributions (access denied inside the view), so the step is red until stage 1b fixes that.
- The step "Tests" runs `verify` of the UI runtime offline. If its frontend build needs Node or npm packages that the image build did not leave in the shared `~/.cache/mig-env-build-home`, the offline run fails: fetch them online first, as the step does for the surefire provider.

## Storage

GitHub's artifact storage is small and shared across the account. The job therefore never hands the build to another job, and uploads nothing on success. Diagnostics of a failed run are kept for 3 days; a full quota never turns a run red. The Maven and Playwright caches count against the cache limit, not the storage quota.

## What it downloads

Maven Central (`repo.maven.apache.org`) for Maven, enforced by `env/maven-settings.xml` and the host check; `nodejs.org` (Node 24 for Vaadin's frontend build, once per Node version, then cached) and `registry.npmjs.org` (the frontend build's npm packages, and Playwright); official Docker Hub images, each pinned by digest in `env/lib.sh` (`maven:3.9-eclipse-temurin-21`, `eclipse-temurin:21-jre`, `mysql:8.0`, `rabbitmq:3-management-alpine`, `python:3.9.6-slim`; versions in `env/README.md`); the Playwright browser download; the pinned actions `checkout`, `cache`, `upload-artifact`. The app and the tests run on an internal network with no route out, and the copy's DNS answers only its own container names. The smoke browser reaches only `127.0.0.1:18081`.

## Test users

`.github/ci/test-users.sh` writes `~/.config/mig-ci/test-users.properties` (users `admin`, `readonly`, `targets`, `repository` with the permissions of the migration's test users, passwords only as `${MIG_TU_*_PW}` references) and `~/.config/mig-ci/test-users.env` (the passwords). The app reads both files as uid 1000 inside its container (`env/README.md` "Needs"). The properties file holds no password and is mode 644. The password file stays mode 600 and only uid 1000 is added: as the owner when the host user is uid 1000, else with a POSIX ACL (`setfacl -m u:1000:r`), else, without `setfacl`, by giving it to group 1000 with mode 640 (needs `sudo`). It is never made world-readable; the script fails if none of these works. On GitHub-hosted runners the user is uid 1001, so the ACL applies. The passwords are random per run and masked in the log; CI needs no repository secret. The env scripts pass both files to vv8-ui (`APP_ENV_USERS_PROPERTIES`, `APP_ENV_USERS_ENV`); the smoke reads the env file.

## Licence headers

The shell scripts and the workflow carry the project's standard header (`licenses/LICENSE_HEADER_TEMPLATE.txt`), as all of the project's other `.sh` files and most of its `.yml` files do (decision E001 of the migration). `license:check` excludes `.github/**`, so it does not check them. No header on the smoke copies (`.cjs`: kept identical to the workspace tool; the project's JavaScript mostly has none), the smoke config (JSON has no comments) or this note (the project's Markdown has none).

## The smoke tool is a copy

`.github/ci/smoke/smoke.cjs` and `launch.cjs` are copies of `tools/playwright/smoke.cjs` and `launch.cjs` from the migration workspace (commit `de0920c`), unchanged apart from a first comment line. The workspace is not part of this repository. When the workspace tool changes, copy it again.

## Run the same checks locally

Needs bash 4.4+, git, curl, GNU coreutils (`timeout`, `sha256sum`), Docker and Node.js with Playwright.

```sh
.github/ci/test-users.sh                         # or point APP_ENV_USERS_* at your own files
export APP_ENV_PROJECT=ci APP_ENV_PORT=18081 \
  APP_ENV_USERS_PROPERTIES=$HOME/.config/mig-ci/test-users.properties APP_ENV_USERS_ENV=$HOME/.config/mig-ci/test-users.env
env/up.sh
node .github/ci/smoke/smoke.cjs .github/ci/smoke/new-app.json http://127.0.0.1:18081 /tmp/ci-smoke
.github/ci/run-tests.sh /tmp/ci-tests
env/down.sh
```

`.github/ci/run-tests.sh` compiles in the checkout (`target/` folders) and needs the copy running.
