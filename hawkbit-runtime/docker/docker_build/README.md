hawkBit Docker Build
===
This directory contains docker files for building both hawkBit docker flavours:
* _standard_ - hawkBit images without mysql driver
* _mysql_ - with MariaDB Java connector with support for MySQL.
  
Both flavours of are almost the same, just mysql has in addition a MariaDB Java connector and is started, by default, with mysql Spring profile.

For every flavour there are two build types:
* _default_ - takes the hawkBit application jar from the docker build context (a jar of your own build; see _HAWKBIT_JAR_), or, with _HAWKBIT_JAR_SOURCE=download_, downloads an officially released version from https://repo1.maven.org and verifies its signature
* _development/dev_ - uses the local maven repository with built by developer (or just downloaded from any maven repository) hawkBit applications 

## Build overview
Building images supports the following build arguments (i.e. ARG-s which could be passed using _--build-arg_):
* _JAVA_VERSION_ - **[OPTIONAL, if not set a default is used]** the Java version of the eclipse-temurin jre-alpine base image (Java 21 or later) to be used.   
* _HAWKBIT_APP_ - **[OPTIONAL, if not set _hawkbit-update-server_ is used]** the application to be build. Currently, there is just _hawkbit-update-server_ but in future, if hawkBit is split to micro-services, there could be different micro-service apps.
* _HAWKBIT_JAR_SOURCE_ - **[OPTIONAL, if not set _local_ is used]** _local_ copies the jar named by _HAWKBIT_JAR_ from the build context. _download_ fetches the released _HAWKBIT_APP_-_HAWKBIT_VERSION_ jar from https://repo1.maven.org instead. A release downloaded this way must itself be built on the migrated stack (Java 21, Spring Boot 4): the default _HAWKBIT_VERSION_ 0.3.0 is not, and its extract step fails.
* _HAWKBIT_JAR_ - **[OPTIONAL, if not set _HAWKBIT_APP_.jar is used]** (_local_ only) the path of the application jar in the build context
* _HAWKBIT_VERSION_ - **[OPTIONAL, if not set 0.3.0 is used]** (_download_ only) the released application version. Set it to a release built on the migrated stack.
* _CONTAINER_PORT_ - **[OPTIONAL, if not set 8080 is used]** on which the app opens the http server (if available)
* _MARIADB_DRIVER_VERSION_ (mysql flavours only!) - **[OPTIONAL, if not set a default is used]** the version of MariaDB connector to be used

Additionally, tge _development_ builds shall be started with docker build context the local maven repository

## Build standard
Standard flavour could be build, for example, with your own jar copied next to the Dockerfile (the build context is this directory, which also holds the signature keys):
```shell
cp <your build>/hawkbit-update-server-<version>.jar hawkbit-update-server.jar
docker build -t hawkbit_update_server:<version> . -f Dockerfile
```
having that docker uses by default _Dockerfile_ and the _hawkbit-update-server_ is the default _HAWKBIT_APP_. To take the jar from another file name use `--build-arg HAWKBIT_JAR=<file in the context>`.

To download a release instead:
```shell
docker build --build-arg HAWKBIT_JAR_SOURCE=download --build-arg HAWKBIT_VERSION=<release> -t hawkbit_update_server:<release> . -f Dockerfile
```

To build development docker images, e.g. snapshot based, you could use something like:
```shell
docker build -t hawkbit_update_server:0.4.0-SNAPSHOT -f Dockerfile_dev ~/.m2/repository
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
docker build -t hawkbit_update_server:0.4.0-SNAPSHOT-mysql -f Dockerfile_dev-mysql ~/.m2/repository
```
Note that here you have to use your maven repository containing the hawkBit app as docker build context, in the example case _~/.m2/repository_ 