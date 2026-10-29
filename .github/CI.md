# CI

One GitHub Actions workflow, `.github/workflows/ci.yml`: the job `build-test-smoke` on a GitHub-hosted `ubuntu-24.04` runner, behind the small job `already-green` (see "When it runs"). It builds the UI path (Java 21, Vaadin 25), starts one isolated copy of the app with the env scripts (`env/README.md`), logs in every test user and opens their views, and runs the test suite. In parallel, the job `e2e` builds and starts its own copy and runs the UI end-to-end tests against it (see "Job e2e").

## When it runs

- Push to `main`, `mig-dev`, `stage/**`, `stabilise/**`, `fix/**`.
- Manually: Actions, CI, "Run workflow" (`workflow_dispatch`).

There is no `pull_request` trigger: the checks of a push are attached to the commit, so they show on a pull request of that branch, and a second run for the same commit would only repeat the first.

CI runs once per commit. The first job, `already-green`, asks the GitHub API (with the job's own token, permission `actions: read`, no other) whether this commit already has a successful run of this workflow. If it has, the jobs `build-test-smoke` and `e2e` are skipped and the run ends as a success within a minute: a branch moved to a commit that already passed (a fast-forward merge, a copy of a branch) is not built again. A failed or cancelled earlier run does not count, so that commit runs again. To run a green commit again, start the workflow by hand with `force` ticked. Two runs of one commit started at the same moment (before either has finished) both run: the check only sees finished successes.

No schedule. A newer run on the same ref cancels the one still running. Time limits: 20 minutes for build and start, 5 for the Playwright install, 10 for the smoke, 20 for the tests, 60 for the job; in `e2e`, 35 for `env/e2e.sh` (build, start, tests), 45 for the job. A step over its limit fails, so its diagnostics are uploaded.

## Steps

| Step | Does | Fails when |
|---|---|---|
| (job `already-green`) | See "When it runs": sets `skip` when the commit has a successful run; `build-test-smoke` and `e2e` then do not run | the API call fails (the run then fails rather than guessing) |
| Restore Maven repository, Node and npm cache, Playwright browser | `actions/cache` of `~/.m2/repository` and `~/.cache/mig-env-build-home` (Vaadin's Node and the npm cache; keys: hash of every `pom.xml`) and `~/.cache/ms-playwright` (key: Playwright version) | never |
| Test users | `.github/ci/test-users.sh`: four throw-away users with new random passwords (see "Test users") | |
| Build and start | `env/up.sh`. Builds the app image from the commit: Maven 3.9.16 / JDK 21.0.12.1 in Docker (the image `env/lib.sh` pins by digest), `-pl hawkbit-runtime/hawkbit-vv8-ui,hawkbit-runtime/hawkbit-mgmt-server -am -DskipTests package` with `env/maven-settings.xml` and `-DskipNexusStaging`. Then starts copy `ci` on an internal Docker network with MySQL 8.0 and RabbitMQ 3, seeds it, and serves the UI on `127.0.0.1:18081` | the build fails; Maven or the frontend build downloaded from a host other than `repo.maven.apache.org`, `nodejs.org` and `registry.npmjs.org` (the check is in `env/up.sh`; it sees Maven's transfers and the Node download, not npm's); the app does not start |
| Install Playwright | `npm install playwright@1.63.0` outside the checkout, then the Chromium headless shell | |
| Smoke | `.github/ci/smoke/smoke.cjs` with `.github/ci/smoke/new-app.json`: each test user logs in, opens `/UI/` and each view the user's permissions show by its route (e.g. `/UI/deployment`), then each of them from the menu (36 view checks). The browser may reach only the app's host; every other request is refused and listed | a login fails (that user's checks fail; the next user still runs), a view has a console error, an HTTP status of 400 or more, a collapsed layout, leaves the app, or shows Flow's or the app's error page (`errorTexts` in the config: Flow's "There was an exception while trying to navigate to", the app's "No such view: " and "No access to view: "; these pages answer HTTP 200 with no console error) |
| Tests | `.github/ci/run-tests.sh`. First a fetch (online, mirrors only) of the surefire JUnit provider, by running one unit test of `hawkbit-core` (`Base62UtilTest`), then `-pl hawkbit-runtime/hawkbit-vv8-ui -am -fae verify` offline in Docker on the copy's internal network, in the same pinned Maven image (taken from `env/lib.sh`), with its RabbitMQ (`RABBITMQ_TEST_HOSTNAME=rabbitmq`, `RABBITMQ_SERVER_REQUIRED=true`), test classes in alphabetical order (`-Dsurefire.runOrder=alphabetical`): the default order follows the file system and changes between runners, and some tests depend on it. Runs even when the smoke failed | a test fails; Maven downloaded from another host; no test ran; the broker is unreachable (the 89 DMF integration tests would otherwise skip silently) |
| Stop | `env/down.sh`, always. Saves the app and DNS logs to `~/ci-out/env-logs/` (`APP_ENV_LOG_DIR`), removes the copy | never |
| Summary | `.github/ci/summary.sh`: writes the job summary (and the same lines to the log): Maven download hosts, build and start times, smoke result and blocked hosts, test counts, the DNS names the copy looked up. A row whose input is missing says "not run" or is left out | never (the script always exits 0; the step also has `continue-on-error`) |
| Upload diagnostics | Only when a step failed or ran over its time limit (not when a newer push cancelled the run): `~/ci-out/` (step output, Maven build log, app and DNS logs, smoke screenshots and results, test logs), surefire and failsafe `*.txt` reports. Retention 3 days, `continue-on-error` | never |

## Job e2e

The UI end-to-end tests (`env/README.md` "End-to-end tests"): Playwright for Java and JUnit 5 in the Maven profile `e2e`, module `hawkbit-runtime/hawkbit-vv8-ui-e2e`, headless Chromium. Own job on its own runner, in parallel with `build-test-smoke`, so it builds the image itself: no build is passed between jobs.

| Step | Does | Fails when |
|---|---|---|
| Restore Maven repository, Node and npm cache, Playwright browser | As in `build-test-smoke`, but the Maven repository has its own key (`maven-e2e-...`; it also holds Playwright for Java), restored from `build-test-smoke`'s on a miss. The browser cache is the smoke's: the same Chromium headless shell | never |
| Test users | `.github/ci/test-users.sh`, as in `build-test-smoke` | |
| End-to-end tests | `env/e2e.sh`: `env/up.sh` (build and start copy `ci` as in "Build and start"), then on the runner's JDK 21 and Maven 3.9 (`JAVA_HOME_21_X64`; the step prints `mvn -v`) `mvn -Pe2e -pl hawkbit-runtime/hawkbit-vv8-ui-e2e verify` against `127.0.0.1:18081`: Playwright's CLI installs the Chromium headless shell if the cache has none, failsafe runs the tests, each test class after `env/reset.sh`. Then `env/down.sh`, always | the copy does not start; a test fails (a step's check, a browser error, a request to a host other than the app's); Maven or the browser download used a host outside the allowed ones (`repo.maven.apache.org` and the image build's hosts; `cdn.playwright.dev`, `playwright.download.prss.microsoft.com`) |
| Upload diagnostics | Only on failure: `~/ci-out/` (`e2e.log`, the env logs), the screenshot of each failing test (`target/e2e/`) and the failsafe reports. Retention 3 days, `continue-on-error` | never |

The step "Build and start" is the deployable artefact per commit: the image `mig-new-hawkbit:<commit>` with both jars, built and run in the same job. It is not kept after the run.

## State on the new app

Stage 1a ticket 1a-05 moved the build and run images to Java 21; ticket 1a-06 (green build) switched the smoke to the new UI's selectors and routes (`.github/ci/smoke/new-app.json`). CI runs on GitHub on pushes to `petrixh/v8-v25-poc-3`. This note does not copy the state of the latest run: see `gh run list -R petrixh/v8-v25-poc-3 -b stage/1a-compile`. Dated anchor: the first full run on the new stack (1a-11 merge, run 40543187946, 2026-10-27) built, started and passed the smoke; the step "Tests" failed on `MgmtSoftwareModuleResourceTest.downloadArtifact`. Known state, from the same checks run locally on the stage branch with the test-sources tickets merged:

- The build is green with the test-source tickets merged (1a-06): `-DskipTests` still compiles the test sources, so a branch without them has a red "Build and start".
- Smoke: run locally with the same routes, views and `errorTexts` for these four users (and four more migration test users), every check passes, error pages counted as failures: 13 + 11 + 5 + 7 = 36 for the CI users. Before ticket 1a-10 (non-admin access), `readonly` stopped at its login on Flow's error page.
- The step "Tests" runs `verify` of the UI runtime offline. If its frontend build needs Node or npm packages that the image build did not leave in the shared `~/.cache/mig-env-build-home`, the offline run fails: fetch them online first, as the step does for the surefire provider.

## Storage

GitHub's artifact storage is small and shared across the account. The job therefore never hands the build to another job, and uploads nothing on success. Diagnostics of a failed run are kept for 3 days; a full quota never turns a run red. The Maven and Playwright caches count against the cache limit, not the storage quota; `e2e` keeps a second Maven repository cache (the first plus Playwright for Java, about 200 MB more).

## What it downloads

Maven Central (`repo.maven.apache.org`) for Maven, enforced by `env/maven-settings.xml` and the host check; `nodejs.org` (Node 24 for Vaadin's frontend build, once per Node version, then cached) and `registry.npmjs.org` (the frontend build's npm packages, and Playwright); official Docker Hub images, each pinned by digest in `env/lib.sh` (`maven:3.9-eclipse-temurin-21`, `eclipse-temurin:21-jre`, `mysql:8.0`, `rabbitmq:3-management-alpine`, `python:3.9.6-slim`; versions in `env/README.md`); the Playwright browser download (`cdn.playwright.dev`); Playwright for Java for `e2e` from Maven Central; the pinned actions `checkout`, `cache`, `upload-artifact`. The app and the tests run on an internal network with no route out, and the copy's DNS answers only its own container names. The smoke browser and the `e2e` browser reach only `127.0.0.1:18081`. The `e2e` job's JDK 21 and Maven are the runner image's, not pinned: their versions follow the runner image.

## Test users

`.github/ci/test-users.sh` writes `~/.config/mig-ci/test-users.properties` (users `admin`, `readonly`, `targets`, `repository` with the permissions of the migration's test users, passwords only as `${MIG_TU_*_PW}` references) and `~/.config/mig-ci/test-users.env` (the passwords). The app reads both files as uid 1000 inside its container (`env/README.md` "Needs"). The properties file holds no password and is mode 644. The password file stays mode 600 and only uid 1000 is added: as the owner when the host user is uid 1000, else with a POSIX ACL (`setfacl -m u:1000:r`), else, without `setfacl`, by giving it to group 1000 with mode 640 (needs `sudo`). It is never made world-readable; the script fails if none of these works. On GitHub-hosted runners the user is uid 1001, so the ACL applies. The passwords are random per run and masked in the log; CI needs no repository secret. The env scripts pass both files to vv8-ui (`APP_ENV_USERS_PROPERTIES`, `APP_ENV_USERS_ENV`); the smoke reads the env file.

## Licence headers

The shell scripts and the workflow carry the project's standard header (`licenses/LICENSE_HEADER_TEMPLATE.txt`), as all of the project's other `.sh` files and most of its `.yml` files do (decision E001 of the migration). `license:check` excludes `.github/**`, so it does not check them. No header on the smoke copies (`.cjs`: kept identical to the workspace tool; the project's JavaScript mostly has none), the smoke config (JSON has no comments) or this note (the project's Markdown has none).

## The smoke tool is a copy

`.github/ci/smoke/smoke.cjs` and `launch.cjs` are copies of `tools/playwright/smoke.cjs` and `launch.cjs` from the migration workspace (commit `c47508e`), unchanged apart from a first comment line. The workspace is not part of this repository. When the workspace tool changes, copy it again.

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
env/e2e.sh                                       # job e2e: start, end-to-end tests, remove (needs JDK 21 and Maven)
```

`.github/ci/run-tests.sh` compiles in the checkout (`target/` folders) and needs the copy running.
