hawkBit Docker
===

# Images

The compose files and the commands below use the image names `hawkbit/hawkbit-update-server`, `hawkbit/hawkbit-ddi-server`, `hawkbit/hawkbit-dmf-server`, `hawkbit/hawkbit-mgmt-server` and `hawkbit/hawkbit-vv8-ui`. Build these images from this source first. The Eclipse hawkBit project publishes images with the same names on Docker Hub, but they hold its own releases (with the earlier Vaadin 8 UI), and Docker pulls them when there is no local image of that name.

```bash
# in the repository root, with Java 21: puts the jars into ~/.m2/repository
$ mvn clean install
$ cd hawkbit-runtime/docker/docker_build
# standard flavour: hawkbit/<app>:0.4.0-SNAPSHOT and hawkbit/<app>:latest
$ ./build_all_dev.sh
# mysql flavour: hawkbit/<app>:0.4.0-SNAPSHOT-mysql and hawkbit/<app>:latest-mysql
$ ./build_all_dev.sh -f mysql
```

The mysql flavour needs a MariaDB driver in the Maven repository: see [docker_build/README.md](docker_build/README.md).

# Setup

All commands run in this folder (`hawkbit-runtime/docker`).

## A: Docker Container

Start the hawkBit Update Server as a single container (standard flavour, embedded H2 database)

```bash
$ docker run -d -p 8080:8080 hawkbit/hawkbit-update-server:latest
```

## B: Docker Compose

Start the hawkBit Update Server together with an MySQL and RabbitMQ instance as containers (mysql flavour)

```bash
# Requires Docker Compose to be installed
$ docker compose -f docker-compose-monolith-mysql.yml up -d
```

Or start hawkBit as micro services (DDI, DMF and Management server and the Management UI, mysql flavour) together with an MySQL and RabbitMQ instance

```bash
$ docker compose -f docker-compose-micro-services-mysql.yml up -d
```

## C: Docker Stack

Start only an MySQL and RabbitMQ instance as services within a swarm, e.g. for hawkBit started from the jars or an IDE (`docker-compose-deps-postgres.yml` does the same with PostgreSQL)

```bash
$ docker swarm init
$ docker stack deploy -c docker-compose-deps-mysql.yml hawkbit
```

# Access

| Service / Container | URL | Login | A | B (monolith) | B (micro services) | C |
|---|---|---|---|---|---|---|
| hawkBit Update Server | [http://localhost:8080/](http://localhost:8080/) | admin:admin | &#10003; | &#10003; |  |  |
| hawkBit Management server (Management API) | [http://localhost:8080/](http://localhost:8080/) | admin:admin |  |  | &#10003; |  |
| hawkBit DDI server | [http://localhost:8081/](http://localhost:8081/) | - |  |  | &#10003; |  |
| hawkBit Management UI | [http://localhost:8082/UI/](http://localhost:8082/UI/) | admin:admin |  |  | &#10003; |  |
| MySQL | localhost:3306/hawkbit | root |  | &#10003; | &#10003; | &#10003; |
| RabbitMQ | [http://localhost:15672](http://localhost:15672) | guest:guest |  | &#10003; | &#10003; | &#10003; |

# Configuration

You can override application.properties by setting an environment variable SPRING_APPLICATION_JSON for hawkbit container.
```
hawkbit:
    image: "hawkbit/hawkbit-update-server:latest-mysql"
    environment:
      SPRING_APPLICATION_JSON: '{
        "spring.datasource.url": "jdbc:mariadb://mysql:3306/hawkbit",
        "spring.rabbitmq.host": "rabbitmq",
        "spring.rabbitmq.username": "guest",
        "spring.rabbitmq.password": "guest",
        "spring.datasource.username": "root",
        "hawkbit.server.im.users[0].username": "hawkbit",
        "hawkbit.server.im.users[0].password": "{noop}isAwesome!",
        "hawkbit.server.im.users[0].firstname": "Eclipse",
        "hawkbit.server.im.users[0].lastname": "HawkBit",
        "hawkbit.server.im.users[0].permissions": "ALL"
      }'
```
