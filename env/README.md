# Reproducible environment

One command starts one isolated copy of the app: the Vaadin 8 UI (`hawkbit-vv8-ui`) with `hawkbit-mgmt-server`, MySQL 8.0 and RabbitMQ 3, seeded with a small synthetic data set. Several copies run side by side, each with its own network, database, broker and port. The scripts follow the replay runner's env-script contract (`up.sh`, `reset.sh`, `down.sh`, configured through `APP_ENV_*`).

Needs: bash, git, curl and Docker on the host. Nothing else is installed: Maven and Java run in Docker.

## Commands

```sh
APP_ENV_PROJECT=copy-a APP_ENV_PORT=18181 env/up.sh     # build the image for HEAD if missing, start, seed; returns when the UI answers
APP_ENV_PROJECT=copy-a APP_ENV_PORT=18181 env/reset.sh  # back to the seed state
APP_ENV_PROJECT=copy-a env/down.sh                      # remove the copy
```

The UI is at `http://127.0.0.1:$APP_ENV_PORT/UI/`. `up.sh --no-build` starts from an existing image and fails if there is none.

| Script | Does | Exit |
|---|---|---|
| `up.sh [--no-build]` | Builds the app image for `HEAD` if it does not exist yet (`--no-build`: it must exist). Starts the copy, runs the seed hook, takes the seed dump, starts the UI; about 40-55 s with the image in place. Refuses to start when the copy already exists. On failure it leaves what it started, for `down.sh` | 0 once `/UI/login` answers 200 |
| `reset.sh` | Returns the running copy to the seed state (see Reset) | 0 once the UI answers again |
| `down.sh` | Saves the DNS log and app logs, then removes every container, network and volume of the copy and its state dir. Safe after a half-finished or killed `up.sh` and when the copy is gone | always 0 |

## Settings

| Variable | Default | Meaning |
|---|---|---|
| `APP_ENV_PROJECT` | `hawkbit-env` | Copy name (`^[a-z0-9][a-z0-9_.-]*$`). Prefix of every container, network and volume of the copy, and the value of their label `mig.env.project` |
| `APP_ENV_PORT` | `18081` | Host port of the UI, bound to 127.0.0.1 only |
| `APP_ENV_CONTAINER` | `<project>-app` | Name of the vv8-ui container (the one whose log a test runner reads) |
| `APP_ENV_USERS_PROPERTIES` | none | Properties file with extra users (`hawkbit.server.im.users[N].*`), mounted read-only into vv8-ui and loaded with `--spring.config.additional-location`. Without it only the app's default admin exists |
| `APP_ENV_USERS_ENV` | none | Env file passed to vv8-ui with `--env-file` (e.g. the passwords the properties file references) |
| `APP_ENV_SEED` | `env/seed/seed.sh` | Seed hook; `none` for an empty seed (fresh schema) |
| `APP_ENV_STATE_DIR` | `env/.state` | Per-copy state, build scratch, saved logs (git-ignored) |
| `APP_ENV_LOG_DIR` | `<state dir>/logs` | Build logs and the logs `down.sh` saves |
| `APP_ENV_MAVEN_REPO` | `~/.m2/repository` | Local Maven repository for the build |
| `APP_ENV_MAVEN_SETTINGS` | `env/maven-settings.xml` | Maven settings: every repository mirrored to Maven Central, except `maven.vaadin.com` for Vaadin add-ons |
| `APP_ENV_MAVEN_HOSTS` | `repo.maven.apache.org maven.vaadin.com` | The only hosts Maven may download from; the build fails if its log shows another |
| `APP_ENV_IMAGE_REPO` | `mig-old-hawkbit` | Image name; the tag is the first 12 characters of the commit |
| `APP_ENV_REBUILD` | `0` | `1` rebuilds the image even if it exists |

Test-user files are only read by Docker from where they are; the scripts never copy them into the repo, an image or a log.

## What runs

Per copy, all labelled `mig.env.project=<project>`:

| Container | Image | Role |
|---|---|---|
| `<project>-dns` | `python:3.9.6-slim` | Logging DNS for the copy (see Isolation) |
| `<project>-mysql` (alias `mysql`) | `mysql:8.0` | Database `hawkbit`, volume `<project>-mysql-data` |
| `<project>-rabbitmq` (alias `rabbitmq`) | `rabbitmq:3-management-alpine` | Broker for DMF and the Spring Cloud Bus between the two JVMs, volume `<project>-rabbitmq-data` |
| `<project>-mgmt` | the app image | `hawkbit-mgmt-server`: creates the schema with Flyway, serves the Management API (used by the seed), runs the rollout and auto-assign schedulers |
| `$APP_ENV_CONTAINER` | the app image | `hawkbit-vv8-ui` on port 8082: the UI |
| `<project>-proxy` | `python:3.9.6-slim` | Inbound-only TCP forward from 127.0.0.1:`$APP_ENV_PORT` to the UI |

Networks: `<project>-int` (`--internal`, subnet chosen by Docker) holds everything; `<project>-edge` only the proxy's published port. Volume `<project>-artifacts` is the artifact store both JVMs share.

Memory per copy, measured: about 2.45 GB for its containers (each JVM about 0.9 GB, MySQL about 0.45 GB, RabbitMQ about 0.14 GB), plus about 0.33 GB for a test browser.

Both JVMs run as in the project's docs for MySQL: Spring profile `mysql`, MariaDB JDBC driver 3.1.4 on the loader path, `-Xms768m -Xmx768m`, uid 1000.

**Runtime mode: production.** `hawkbit-ui/src/main/resources/hawkbit-ui-defaults.properties` sets `vaadin.servlet.productionMode=true` and the scripts do not override it; the vv8-ui log shows `Set servlet init parameter [productionMode] = [true]` at start.

**mgmt-server's admin** gets a new random password per copy (`SPRING_SECURITY_USER_PASSWORD`, in the copy's state dir, mode 600, never printed). Only the seed uses it; mgmt-server has no port on the host. The users of vv8-ui are unchanged.

## The image

`up.sh` builds `mig-old-hawkbit:<commit>` from `HEAD` (uncommitted changes are not in it): `git archive HEAD` into the state dir, then Maven 3.9 / JDK 17 in `maven:3.9-eclipse-temurin-17`, `-pl hawkbit-runtime/hawkbit-vv8-ui,hawkbit-runtime/hawkbit-mgmt-server -am -DskipTests package` with `-DskipNexusStaging`, plus `org.mariadb.jdbc:mariadb-java-client:3.1.4` from Maven Central. The two jars and the driver go on `eclipse-temurin:17-jre` (pinned by digest in `lib.sh`) with `env/docker/Dockerfile`; the build scratch is then deleted. The project's own `hawkbit-runtime/docker` files are not used. Copies started together build once: the build holds a lock in the state dir, and the copies start in parallel after it. With a warm Maven repository the build takes about 80 s.

## Seed

`up.sh` runs the seed hook once per copy, on the fresh schema, after mgmt-server is up. The default hook, `env/seed/seed.sh`, runs `env/seed/seed.py` in a `python:3.9.6-slim` container on the copy's internal network; it creates everything through the app's own Management API, with fixed names and values. Only ids and timestamps come from the app.

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

To change the seed, edit `seed.py` (and this table), or point `APP_ENV_SEED` at another executable. A hook gets `SEED_NETWORK`, `SEED_RESOLV`, `SEED_MGMT_URL`, `SEED_CREDENTIALS` (env file with `MGMT_USER`, `MGMT_PASSWORD`) and `SEED_LABEL` (put it on any container it starts), and exits 0 when the seed is in place.

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
- Their `/etc/resolv.conf` points at `<project>-dns`, which answers only this copy's names (`mysql`, `rabbitmq`, `<project>-mysql`, `<project>-rabbitmq`, `<project>-mgmt`, `$APP_ENV_CONTAINER`), via Docker's DNS, and returns NXDOMAIN for everything else, without forwarding it. Every query is logged to `<state dir>/copies/<project>/dns/dns.log`; `down.sh` keeps a copy in the log dir.
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

## With the replay runner

```sh
node tools/playwright/replay/run-parallel.cjs --env-dir <repo>/env --n 2 --sample
```

The runner sets `APP_ENV_PORT`, `APP_ENV_PROJECT` and `APP_ENV_CONTAINER` per copy and passes its own environment through, so set `APP_ENV_USERS_*` (and the Maven settings) in the shell that starts it. The first copy builds the image; the others start with `--no-build`.
