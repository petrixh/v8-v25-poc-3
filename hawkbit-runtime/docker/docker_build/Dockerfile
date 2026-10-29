# set Java
ARG JAVA_VERSION=21.0.12.1_1
# where the application jar comes from: "local" (default) takes it from the docker build context (a jar of your own
# build, see HAWKBIT_JAR), "download" fetches the officially released ${HAWKBIT_APP}-${HAWKBIT_VERSION}.jar from
# https://repo1.maven.org and verifies its signature with KEY
ARG HAWKBIT_JAR_SOURCE=local

# stage "jar-local": the jar from the build context
FROM scratch AS jar-local
ARG HAWKBIT_APP=hawkbit-update-server
ARG HAWKBIT_JAR=${HAWKBIT_APP}.jar
COPY ${HAWKBIT_JAR} /app.jar

# stage "jar-download": the released jar, signature verified
FROM eclipse-temurin:${JAVA_VERSION}-jre-alpine AS jar-download

ARG HAWKBIT_APP=hawkbit-update-server
ARG HAWKBIT_VERSION=0.3.0

COPY KEY .

RUN set -x &&\
 apk add --no-cache --virtual build-dependencies gnupg unzip libressl wget &&\
 gpg --import KEY &&\
 wget -O /app.jar --no-verbose https://repo1.maven.org/maven2/org/eclipse/hawkbit/${HAWKBIT_APP}/${HAWKBIT_VERSION}/${HAWKBIT_APP}-${HAWKBIT_VERSION}.jar &&\
 wget -O /app.jar.asc --no-verbose https://repo1.maven.org/maven2/org/eclipse/hawkbit/${HAWKBIT_APP}/${HAWKBIT_VERSION}/${HAWKBIT_APP}-${HAWKBIT_VERSION}.jar.asc &&\
 gpg --batch --verify /app.jar.asc /app.jar &&\
 apk del build-dependencies &&\
 rm /app.jar.asc /KEY

FROM jar-${HAWKBIT_JAR_SOURCE} AS jar

# extracts spring layers from the app jar (to optimize boot)
FROM eclipse-temurin:${JAVA_VERSION}-jre-alpine AS build

ENV BUILD_DIR=/opt/hawkbit_build

COPY --from=jar /app.jar /app.jar

RUN set -x &&\
 mkdir -p ${BUILD_DIR} &&\
 cd ${BUILD_DIR} &&\
 java -Djarmode=tools -jar /app.jar extract --layers --launcher --destination . &&\
 rm /app.jar

FROM eclipse-temurin:${JAVA_VERSION}-jre-alpine

RUN addgroup -S hawkbit_group && adduser -D hawkbit -G hawkbit_group
RUN mkdir -p /artifactrepo && chown -R hawkbit /artifactrepo
USER hawkbit

ENV BUILD_DIR=/opt/hawkbit_build
COPY --from=build ${BUILD_DIR}/dependencies/ ./
COPY --from=build ${BUILD_DIR}/spring-boot-loader/ ./
COPY --from=build ${BUILD_DIR}/snapshot-dependencies/ ./
COPY --from=build ${BUILD_DIR}/application/ ./

ARG CONTAINER_PORT=8080
ENV JAVA_OPTS="-Xms768m -Xmx768m -XX:MaxMetaspaceSize=250m -XX:MetaspaceSize=250m -Xss300K -XX:+UseG1GC -XX:+UseStringDeduplication -XX:+UseCompressedOops -XX:+HeapDumpOnOutOfMemoryError"

VOLUME "/artifactrepo"
EXPOSE ${CONTAINER_PORT}
ENTRYPOINT ["sh", "-c", "java ${JAVA_OPTS} org.springframework.boot.loader.launch.JarLauncher ${@}"]
