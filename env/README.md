# Reproducible environment

One command starts one isolated copy of the app: the UI runtime (`hawkbit-vv8-ui`, which keeps its name from the earlier Vaadin 8 UI and serves the Vaadin 25 UI on Java 21) with `hawkbit-mgmt-server`, MySQL 8.0 and RabbitMQ 3, seeded with a small synthetic data set. Several copies run side by side, each with its own network, database, broker and port. The scripts follow the replay runner's env-script contract (`up.sh`, `reset.sh`, `down.sh`, configured through `APP_ENV_*`).

## Needs

On the host, checked by `up.sh` and `reset.sh` at start; nothing else is installed (Maven, Node and Java run in Docker):

- **bash 4.4 or later.** Older bash (stock macOS has 3.2) fails on empty arrays under `set -u`. On macOS install a current bash and GNU coreutils.
- **git, curl, Docker.**
- **GNU coreutils `timeout` and `sha256sum`.** Every wait and probe has a time limit through `timeout`.
- **`flock`** (util-linux), optional. With it, copies started together build the image once and the others wait (at most 1800 s). Without it there is no build lock: copies started together on a commit without an image each build it, in their own build dir. The result is the same image, built more than once. The replay runner avoids this anyway: only its first copy builds.
- **A Docker daemon that sees this host's paths.** The scripts bind-mount files from `env/` and the state dir into containers, so the daemon must run on this host (or share these paths, as Docker Desktop does for the folders it shares). A remote `DOCKER_HOST` does not work.
- **Files readable by uid 1000** for `APP_ENV_USERS_PROPERTIES` and `APP_ENV_USERS_ENV`: the app runs as uid 1000 in its container and reads them there. A file of mode 600 works when the host user is uid 1000.

## Commands

```sh
APP_ENV_PROJECT=copy-a APP_ENV_PORT=18181 env/up.sh     # build the image for HEAD if missing, start, seed; returns when the UI answers
APP_ENV_PROJECT=copy-a APP_ENV_PORT=18181 env/reset.sh  # back to the seed state
APP_ENV_PROJECT=copy-a env/down.sh                      # remove the copy
APP_ENV_PROJECT=copy-a APP_ENV_PORT=18181 env/e2e.sh    # start, run the UI end-to-end tests, remove (see End-to-end tests)
```

The UI is at `http://127.0.0.1:$APP_ENV_PORT/UI/`. `up.sh --no-build` starts from an existing image and fails if there is none.

| Script | Does | Exit |
|---|---|---|
| `up.sh [--no-build]` | Builds the app image for `HEAD` if it does not exist yet (`--no-build`: it must exist). Starts the copy, runs the seed hook, takes the seed dump, gives mgmt-server the test users (if any), starts the UI; about 40-55 s with the image in place. Refuses to start when the copy already exists (any container, network or volume with its label, or a volume with one of its names). On failure it leaves what it started, for `down.sh` | 0 once `/UI/login` answers 200 |
| `reset.sh` | Returns the running copy to the seed state (see Reset) | 0 once the UI answers again |
| `down.sh` | Saves the DNS log and app logs, then removes every container, network and volume of the copy and its state dir. Safe after a half-finished or killed `up.sh` and when the copy is gone | always 0 |
| `e2e.sh [--no-build] [--running] [-- <maven args>]` | `up.sh`, then the UI end-to-end tests against the copy, then `down.sh` (`--running`: a copy that is already up, left running). See End-to-end tests | Maven's: 0 when every test passed |

## Settings

| Variable | Default | Meaning |
|---|---|---|
| `APP_ENV_PROJECT` | `hawkbit-env` | Copy name (`^[a-z0-9][a-z0-9_.-]*$`). Prefix of every container, network and volume of the copy, and the value of their label `mig.env.project` |
| `APP_ENV_PORT` | `18081` | Host port of the UI, bound to 127.0.0.1 only |
| `APP_ENV_CONTAINER` | `<project>-app` | Name of the vv8-ui container (the one whose log a test runner reads) |
| `APP_ENV_USERS_PROPERTIES` | none | Properties file with extra users (`hawkbit.server.im.users[N].*`), mounted read-only into vv8-ui and mgmt-server and loaded with `--spring.config.additional-location` (mgmt-server: after the seed, see "mgmt-server's admin"). Without it only the app's default admin exists (on mgmt-server: admin with the copy's random password) |
| `APP_ENV_USERS_ENV` | none | `KEY=value` file (e.g. the passwords the properties file references as `${KEY}`), mounted read-only into vv8-ui and mgmt-server and loaded as a second properties file, not as environment variables. Lines are `KEY=value` or comments; a value may not contain `\` or start with a blank (`up.sh` refuses the file and names the line, never the value) |
| `APP_ENV_SEED` | `env/seed/seed.sh` | Seed hook; `none` for an empty seed (fresh schema) |
| `APP_ENV_STATE_DIR` | `env/.state` | Per-copy state, build scratch, saved logs (git-ignored) |
| `APP_ENV_LOG_DIR` | `<state dir>/logs` | Build logs and the logs `down.sh` saves |
| `APP_ENV_MAVEN_IMAGE` | `maven:3.9-eclipse-temurin-21`, pinned (see The image) | Image the build runs Maven in |
| `APP_ENV_MAVEN_REPO` | `~/.m2/repository` | Local Maven repository for the build |
| `APP_ENV_BUILD_HOME` | `~/.cache/mig-env-build-home` | Home of the Maven container, mounted at `/build-home` (`HOME` and `-Duser.home`): Vaadin's frontend build keeps its Node there (`.vaadin`) and npm its cache (`.npm`), so they are downloaded once, not on every build |
| `APP_ENV_MAVEN_SETTINGS` | `env/maven-settings.xml` | Maven settings: every repository mirrored to Maven Central |
| `APP_ENV_MAVEN_HOSTS` | `repo.maven.apache.org nodejs.org registry.npmjs.org` | The only hosts a build may download from (Maven Central; Node and the npm registry for the frontend build); the build fails if its log shows a download from another (see The image) |
| `APP_ENV_IMAGE_REPO` | `mig-new-hawkbit` | Image name; the tag is the first 12 characters of the commit. Distinct from the old app's `mig-old-hawkbit`: old and new never share an image |
| `APP_ENV_REBUILD` | `0` | `1` rebuilds the image even if it exists |

Test-user files are only mounted from where they are; the scripts never copy them into the repo, an image or a log.

**Credentials and `docker inspect`.** No password of the copy is in a container's environment or command line: mgmt-server's admin password, the seed's credentials and both test-user files reach the containers as read-only mounted files. `docker inspect` shows only their paths. Two limits remain. Anyone who can use Docker on the host can still read the files through the containers (`docker exec`, `docker cp`): Docker access is root access. And the repo's own defaults (RabbitMQ `guest`/`guest`, MySQL root without a password) are passed as environment variables; they are reachable only inside the copy.

## What runs

Per copy, all labelled `mig.env.project=<project>`:

| Container | Image | Role |
|---|---|---|
| `<project>-dns` | `python:3.9.6-slim` (Python 3.9.6) | Logging DNS for the copy (see Isolation) |
| `<project>-mysql` (alias `mysql`) | `mysql:8.0` (8.0.46) | Database `hawkbit`, volume `<project>-mysql-data` |
| `<project>-rabbitmq` (alias `rabbitmq`) | `rabbitmq:3-management-alpine` (3.13.7) | Broker for DMF and the Spring Cloud Bus between the two JVMs, volume `<project>-rabbitmq-data` |
| `<project>-mgmt` | the app image | `hawkbit-mgmt-server`: creates the schema with Flyway, serves the Management API (used by the seed), runs the rollout and auto-assign schedulers |
| `$APP_ENV_CONTAINER` | the app image | `hawkbit-vv8-ui` on port 8082: the UI |
| `<project>-proxy` | `python:3.9.6-slim` | Inbound-only TCP forward from 127.0.0.1:`$APP_ENV_PORT` to the UI |

Every image is an official Docker Hub image, pinned by its multi-arch digest in `lib.sh`, so every host runs the same versions (in brackets). To move to another version, change the digest in `lib.sh` and the version here. Python 3.9.6 is past its end of life; it runs only the env's helpers (DNS, proxy, seed), never app code, and is the version the migration's pre-check used.

Networks: `<project>-int` (`--internal`, subnet chosen by Docker) holds everything; `<project>-edge` only the proxy's published port. Volume `<project>-artifacts` is the artifact store both JVMs share.

Memory per copy, measured: about 2.45 GB for its containers (each JVM about 0.9 GB, MySQL about 0.45 GB, RabbitMQ about 0.14 GB), plus about 0.33 GB for a test browser.

Both JVMs run with the settings the migration's pre-check proved on the old app, adapted to the new stack: Spring profile `mysql`, MariaDB JDBC driver 3.5.10 (the version Spring Boot 4.1.2 manages; 3.1.4 on the old app) on the loader path (`-Dloader.path`), the launcher `org.springframework.boot.loader.launch.PropertiesLauncher` (Boot 3.2 and later; `org.springframework.boot.loader.PropertiesLauncher` on the old app), `-Xms768m -Xmx768m`, uid 1000. The project's own `hawkbit-runtime/docker/docker_build/Dockerfile-mysql` differs: it puts the driver in `BOOT-INF/lib` and sets more JVM flags. It is not used here.

**Runtime mode: production.** The scripts do not set the Vaadin mode: `hawkbit-ui/src/main/resources/hawkbit-ui-defaults.properties` sets `vaadin.productionMode=true`, the image holds the production build, and the vv8-ui log says `Vaadin is running in production mode.` Development mode cannot be switched on in this image by a property alone: it needs Vaadin's development dependencies and the unpacked class path of an IDE run (`hawkbit-ui/README.md`).

**mgmt-server's admin** gets a new random password per copy, never printed. It is in `mgmt-admin.properties` in the copy's state dir (the dir has mode 700, the file mode 644 so the container's uid 1000 can read it), mounted read-only and loaded with `--spring.config.additional-location`. Only the seed uses it; mgmt-server has no port on the host.

**Test users on mgmt-server.** mgmt-server runs the rollout scheduler, which loads a rollout's creator as a user; a user it does not know ("No such user") leaves the rollout in Creating. So mgmt-server gets the same test-user files as vv8-ui, mounted the same way. It gets them only after the seed: configuring `hawkbit.server.im.users` replaces the default admin, and the seed logs in as admin with the copy's password. `up.sh` runs the seed against mgmt-server without the test users, takes the seed dump, then recreates the mgmt-server container with the files (the admin file is loaded last). From then on, its users are those of the file, as on vv8-ui; admin's password is whatever the file gives it (a file that declares admin with `${spring.security.user.password}` keeps the copy's random password there). Without `APP_ENV_USERS_*` mgmt-server is not recreated and has only its admin.

## The image

`up.sh` builds `mig-new-hawkbit:<commit>` from `HEAD` (uncommitted changes are not in it): `git archive HEAD` into its own build dir in the state dir, then Maven 3.9.16 / JDK 21.0.12.1 in `maven:3.9-eclipse-temurin-21` (pinned by digest), `-pl hawkbit-runtime/hawkbit-vv8-ui,hawkbit-runtime/hawkbit-mgmt-server -am -DskipTests package` with `-DskipNexusStaging`, plus `org.mariadb.jdbc:mariadb-java-client:3.5.10` from Maven Central. The `package` of the UI runtime runs Vaadin's frontend build (`prepare-frontend`, `build-frontend`), which downloads Node (24.x, from `nodejs.org`) and npm packages (from `registry.npmjs.org`) into `APP_ENV_BUILD_HOME`; with no custom frontend files or theme Vaadin uses its pre-built bundle and needs neither. No Vaadin licence key is passed into the build container. The two jars and the driver go on `eclipse-temurin:21-jre` (pinned by digest) with `env/docker/Dockerfile`; the build dir is then deleted, also when the build fails. The project's own `hawkbit-runtime/docker` files are not used. Copies started together build once when `flock` is there (see Needs): the build holds a lock in the state dir, and the copies start in parallel after it. With a warm Maven repository the build takes about 80 s.

Download hosts: `maven-settings.xml` mirrors every repository to Maven Central (`maven.vaadin.com` is no longer exempt: the Vaadin add-ons that needed it are gone; Vaadin's own POMs still name it for pre-release lookups, which go to Central and get a harmless 404). That is what keeps Maven off other hosts. After the build, `up.sh` also reads the Maven log and fails if it shows a download from a host outside `APP_ENV_MAVEN_HOSTS`. The check sees Maven's repository transfers and the Node download the Vaadin plugin logs (`Downloading https://nodejs.org/...`), and only after they happened: it detects a wrong settings file, it does not prevent the download. The npm registry's package downloads are not in Maven's log, so `registry.npmjs.org` is allowed but not observed; npm only talks to the registry in its configuration (no registry is set in the repo).

## Seed

`up.sh` runs the seed hook once per copy, on the fresh schema, after mgmt-server is up, and stops it after 600 s. The default hook, `env/seed/seed.sh`, runs `env/seed/seed.py` in the env's Python container on the copy's internal network, with the credentials as a mounted file; it creates everything through the app's own Management API, with fixed names and values. Only ids and timestamps come from the app.

| Entity | Count | Content |
|---|---|---|
| Target tags | 2 | `Lab`, `Field` |
| Targets | 10 | `seed-dev-01` … `seed-dev-10` ("Seed Device 01" …); 01-04 tagged `Lab`, 05-08 `Field`, 09-10 untagged |
| Software modules | 5 | `Seed OS` 1.0.0 and 2.0.0 (type `os`); `Seed App` 1.0.0 and 1.1.0, `Seed Agent` 1.0.0 (type `application`) |
| Distribution sets | 4 | `Seed OS Base` 1.0.0 (`os`: OS 1.0.0); `Seed Bundle` 1.0.0 (`os_app`: OS 1.0.0, App 1.0.0); `Seed Bundle` 2.0.0 (`os_app`: OS 2.0.0, App 1.1.0, Agent 1.0.0); `Seed Apps` 1.0.0 (`app`: App 1.1.0) |
| Distribution set tags | 1 | `Released` on `Seed OS Base` 1.0.0 and `Seed Bundle` 1.0.0 |
| Target filters | 1 | `Seed Lab devices`: `tag==Lab` |

Plus what the app creates for its tenant on first use (default software module and distribution set types). No artifacts, assignments, actions or rollouts. The hook checks the counts and fails otherwise.

The seed sends one entity per request, so ids follow the order of the table above (`seed-dev-01` has id 1). Two copies' seeds are the same apart from timestamps, the targets' generated security tokens, and the ids of the default types the app creates for its tenant, which it inserts in no fixed order. Within one copy, reset restores all of them exactly. Tests never check generated ids.

To change the seed, edit `seed.py` (and this table), or point `APP_ENV_SEED` at another executable. A hook gets `SEED_NETWORK`, `SEED_RESOLV`, `SEED_MGMT_URL`, `SEED_CREDENTIALS` (env file with `MGMT_USER`, `MGMT_PASSWORD`), `SEED_LABEL` (put it on any container it starts) and `SEED_IMAGE` (the env's pinned Python image), and exits 0 when the seed is in place.

## Reset

The seed state is a `mysqldump` of database `hawkbit` taken right after the seed, with mgmt-server stopped (`<state dir>/copies/<project>/seed.sql`). `reset.sh`:

1. stops mgmt-server and vv8-ui;
2. drops and recreates database `hawkbit` and loads the seed dump;
3. deletes every RabbitMQ queue in vhost `/` (the apps declare theirs again on start);
4. empties the artifact store;
5. starts both JVMs and waits for the UI.

A reset takes about 10 s. The JVM restart is on purpose: the app caches tenant configuration and rollout status in memory, and a database restore under a running app would leave them stale. `up.sh` goes through the same steps 1, 3 and 5 after taking the dump, so a fresh copy and a reset copy start from the same state. Browser sessions do not survive a reset.

## Isolation

- The app containers are only on `<project>-int`, a Docker `--internal` network: no route out, by name or by IP.
- Their `/etc/resolv.conf` points at `<project>-dns`, which answers only this copy's names (`mysql`, `rabbitmq`, `<project>-mysql`, `<project>-rabbitmq`, `<project>-mgmt`, `$APP_ENV_CONTAINER`), via Docker's DNS, and returns NXDOMAIN for everything else, without forwarding it. Each forwarded query uses its own upstream socket and takes only the reply with its own transaction id, so a late reply cannot answer another query. Every query is logged to `<state dir>/copies/<project>/dns/dns.log`; `down.sh` keeps a copy in the log dir.
- The browser gets in only through `<project>-proxy` on 127.0.0.1:`$APP_ENV_PORT`.
- Copies share nothing but images: names, networks, volumes and the state dir are per project.

How to check a running copy:

```sh
# canary: a lookup and a connection by IP from the app container both fail
docker exec <container> curl -sS --max-time 5 https://example.com -o /dev/null   # fails: could not resolve
docker exec <container> curl -sS --max-time 5 https://1.1.1.1 -o /dev/null       # fails: no route
# the DNS log holds only the copy's container names, plus the canary's example.com (NXDOMAIN)
awk '{print $3, $5}' env/.state/copies/<project>/dns/dns.log | sort | uniq -c
# everything of the copy
docker ps -a --filter label=mig.env.project=<project>; docker network ls --filter label=mig.env.project=<project>; docker volume ls --filter label=mig.env.project=<project>
```

## End-to-end tests

UI tests in `hawkbit-runtime/hawkbit-vv8-ui-e2e`: Playwright for Java 1.63.0 and JUnit 5, run by failsafe in the Maven profile `e2e`. They drive a headless Chromium against a running copy, as a user would (roles, labels, visible text), with the browser's time zone set to Europe/Helsinki. The tests are black-box: they use none of the app's classes. The module is in the build only with `-Pe2e`, so the default build neither downloads Playwright nor runs them.

Needs, besides the above: JDK 21 and Maven 3.9 on the host (the tests run there, not in Docker), and on Linux the system libraries Chromium needs (Playwright's `install-deps chromium` installs them on Debian and Ubuntu; GitHub's Ubuntu runners have them). Node.js is not needed. The copy needs the seed and the test user `admin`, with its password as `MIG_TU_ADMIN_PW` in `APP_ENV_USERS_ENV` (as `.github/ci/test-users.sh` writes it).

```sh
# start a copy, run the tests, remove the copy (exit code: Maven's)
APP_ENV_PROJECT=copy-a APP_ENV_PORT=18181 APP_ENV_USERS_PROPERTIES=<file> APP_ENV_USERS_ENV=<file> env/e2e.sh
# against a copy that is already up; one test class
APP_ENV_PROJECT=copy-a APP_ENV_PORT=18181 APP_ENV_USERS_ENV=<file> env/e2e.sh --running -- -Dit.test=Wf01LoginLogoutIT
# Maven only, against any running UI (no reset unless -De2e.reset is given)
mvn -Pe2e -pl hawkbit-runtime/hawkbit-vv8-ui-e2e verify -De2e.baseUrl=http://127.0.0.1:18181 -De2e.usersEnv=<file>
```

| Property (`-D`) | Default | Meaning |
|---|---|---|
| `e2e.baseUrl` | `http://127.0.0.1:18081` | The UI (`e2e.sh`: `http://127.0.0.1:$APP_ENV_PORT`) |
| `e2e.usersEnv` | the environment variable `APP_ENV_USERS_ENV` | The test users' password file (`MIG_TU_<USER>_PW=...`); read at run time, never printed |
| `e2e.reset` | none (`e2e.sh`: `env/reset.sh`) | Command run before each test class to return the copy to the seed state; it gets the `APP_ENV_*` of the shell |
| `e2e.timezone` | `Europe/Helsinki` | Time zone of the browser |

- Each test gets a fresh browser context (its own session). The browser may reach only the UI's host and port: any other request is aborted and fails the test. A console error, page error or HTTP status of 400 or more fails the step it happens in. A target's security token is hidden on every page. Failure messages are cleared of the test users' passwords (Playwright's log of a failed fill can hold the typed value).
- A failing test leaves a screenshot in `hawkbit-runtime/hawkbit-vv8-ui-e2e/target/e2e/`; the reports are in `target/failsafe-reports/`. `e2e.sh` keeps Maven's output in `$APP_ENV_LOG_DIR` (`e2e-<commit>-<time>.log`).
- Downloads: Playwright for Java from Maven Central (its `driver-bundle` jar, about 195 MB, carries Playwright's driver with a Node.js runtime per platform, so nothing is installed for it); at the first run the Chromium headless shell and ffmpeg (about 120 MB) from Playwright's CDN `cdn.playwright.dev` into Playwright's browser cache (`PLAYWRIGHT_BROWSERS_PATH`, default `~/.cache/ms-playwright` on Linux), shared with the CI smoke's Node Playwright of the same version. Playwright may fall back to `playwright.download.prss.microsoft.com`. `e2e.sh` fails when its log shows a download from any other host, or a browser install whose host it cannot read (it parses Playwright's "Downloading ... from <url>" lines).
- A new test: a class `*IT` in `org.eclipse.hawkbit.ui.e2e` that extends `AbstractUiE2eTest`, one `step(...)` per workflow step, with the helpers of `HawkbitUi`. `HawkbitUi` relies on three things that are not public Vaadin API (the Flow client `isActive()` check, the tooltip overlay's `closing` attribute, the `vaadin-tooltip` `target` property; see its Javadoc): check them after a Vaadin update. Keep `playwright.version` in the module's pom equal to `PLAYWRIGHT_VERSION` in `.github/workflows/ci.yml` (the CI job `e2e` fails otherwise).
- In CI the job `e2e` runs `env/e2e.sh` (`.github/CI.md`).

## With the replay runner

```sh
node tools/playwright/replay/run-parallel.cjs --env-dir <repo>/env --n 2 --sample
```

The runner sets `APP_ENV_PORT`, `APP_ENV_PROJECT` and `APP_ENV_CONTAINER` per copy and passes its own environment through, so set `APP_ENV_USERS_*` (and the Maven settings) in the shell that starts it. The first copy builds the image; the others start with `--no-build`.
