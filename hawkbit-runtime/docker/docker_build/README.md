hawkBit Docker Build
===
This directory contains docker files for building both hawkBit docker flavours:
* _standard_ - hawkBit images without mysql driver
* _mysql_ - with MariaDB Java connector with support for MySQL.
  
Both flavours of are almost the same, just mysql has in addition a MariaDB Java connector and is started, by default, with mysql Spring profile.

For every flavour there are two build types:
* _default_ - takes the hawkBit application jar from the docker build context (a jar of your own build; see _HAWKBIT_JAR_), or, with _HAWKBIT_JAR_SOURCE=download_, downloads an officially released version from https://repo1.maven.org and verifies its signature
* _development/dev_ - uses the local maven repository with built by developer (or just downloaded from any maven repository) hawkBit applications 

Images of this version (Java 21, Spring Boot 4, Vaadin 25 UI) are built from your own build: with the _default_ build type from a jar in the build context, or with the _development_ build type from the local maven repository. There is no released version of it to download.

## Build overview
Building images supports the following build arguments (i.e. ARG-s which could be passed using _--build-arg_):
* _JAVA_VERSION_ - **[OPTIONAL, if not set _21.0.12.1_1_ is used]** the Java version of the eclipse-temurin jre-alpine base image (Java 21 or later) to be used.   
* _HAWKBIT_APP_ - **[OPTIONAL, if not set _hawkbit-update-server_ is used]** the application to be build: _hawkbit-update-server_, _hawkbit-ddi-server_, _hawkbit-dmf-server_, _hawkbit-mgmt-server_ or _hawkbit-vv8-ui_.
* _HAWKBIT_JAR_SOURCE_ - **[OPTIONAL, if not set _local_ is used]** (_default_ build type only) _local_ copies the jar named by _HAWKBIT_JAR_ from the build context. _download_ fetches the released _HAWKBIT_APP_-_HAWKBIT_VERSION_ jar from https://repo1.maven.org instead. A release downloaded this way must itself be built on Java 21 / Spring Boot 4: the default _HAWKBIT_VERSION_ 0.3.0 is not, so an image built from it is not expected to work.
* _HAWKBIT_JAR_ - **[OPTIONAL, if not set _HAWKBIT_APP_.jar is used]** (_local_ only) the path of the application jar in the build context
* _HAWKBIT_VERSION_ - **[OPTIONAL, if not set _0.3.0_ is used]** the application version: for _download_, the released version; for the _development_ build type, the version of your build, i.e. the `revision` property of the root `pom.xml`, currently _0.4.0-SNAPSHOT_
* _CONTAINER_PORT_ - **[OPTIONAL, if not set 8080 is used]** the port the image exposes. It does not change the port the app listens on (_hawkbit-ddi-server_ 8081, _hawkbit-vv8-ui_ 8082, the others 8080)
* _MARIADB_DRIVER_VERSION_ (mysql flavours only!) - **[OPTIONAL, if not set _3.1.4_ is used]** the version of MariaDB connector to be used. The build and the tests of this version use _3.5.10_ (the version Spring Boot manages)

Additionally, the _development_ builds shall be started with docker build context the local maven repository

## Build standard
Standard flavour could be build, for example, with your own jar copied next to the Dockerfile (the build context is this directory, which also holds the signature keys):
```shell
cp <your build>/hawkbit-update-server-<version>.jar hawkbit-update-server.jar
docker build -t hawkbit_update_server:<version> . -f Dockerfile
```
having that docker uses by default _Dockerfile_ and the _hawkbit-update-server_ is the default _HAWKBIT_APP_. To take the jar from another file name use `--build-arg HAWKBIT_JAR=<file in the context>`.

To download a release instead:
The local jar is not checked (no checksum, no signature): whoever builds from a local jar owns its provenance, e.g. a CI job builds the jar and passes it to `docker build` in the same job.

```shell
docker build --build-arg HAWKBIT_JAR_SOURCE=download --build-arg HAWKBIT_VERSION=<release> -t hawkbit_update_server:<release> . -f Dockerfile
```

To build development docker images, e.g. snapshot based, first build and install hawkBit (`mvn clean install` in the repository root), then use something like:
```shell
docker build --build-arg HAWKBIT_APP=hawkbit-update-server --build-arg HAWKBIT_VERSION=0.4.0-SNAPSHOT -t hawkbit/hawkbit-update-server:0.4.0-SNAPSHOT -f Dockerfile_dev ~/.m2/repository
```
Note that here you have to use your maven repository containing the hawkBit app as docker build context, in the example case _~/.m2/repository_ 

## Build mysql
Mysql flavour could be build, for example, with your own jar copied next to the Dockerfile as above:
```shell
docker build -t hawkbit_update_server:<version>-mysql . -f Dockerfile-mysql
```
having that the _hawkbit-update-server_ is the default _HAWKBIT_APP_. The MariaDB connector is always downloaded from https://downloads.mariadb.com and verified; _HAWKBIT_JAR_SOURCE=download_ works as above.

To build development docker images, e.g. snapshot based, you could use something like:
```shell
docker build --build-arg HAWKBIT_APP=hawkbit-update-server --build-arg HAWKBIT_VERSION=0.4.0-SNAPSHOT --build-arg MARIADB_DRIVER_VERSION=3.5.10 -t hawkbit/hawkbit-update-server:0.4.0-SNAPSHOT-mysql -f Dockerfile_dev-mysql ~/.m2/repository
```
Note that here you have to use your maven repository containing the hawkBit app and the MariaDB driver as docker build context, in the example case _~/.m2/repository_. `mvn clean install` puts driver _3.5.10_ there.

## Build all development images
`build_all_dev.sh` builds the development images of _hawkbit-ddi-server_, _hawkbit-dmf-server_, _hawkbit-mgmt-server_, _hawkbit-vv8-ui_ and _hawkbit-update-server_ and tags them `hawkbit/<app>:<version>` and `hawkbit/<app>:latest` (mysql flavour: with the suffix `-mysql`), the names the compose files in `../` use:
```shell
./build_all_dev.sh                    # standard flavour, version 0.4.0-SNAPSHOT, context ~/.m2/repository
./build_all_dev.sh -f mysql           # mysql flavour
./build_all_dev.sh -v <version> -r <maven repository>
```
The script does not pass a MariaDB driver version, so the mysql flavour uses the default _3.1.4_, which must then be in the maven repository, e.g. with:
```shell
mvn dependency:get -Dartifact=org.mariadb.jdbc:mariadb-java-client:3.1.4
```
