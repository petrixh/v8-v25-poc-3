# App image for the env scripts (env/README.md): the hawkbit-vv8-ui and hawkbit-mgmt-server jars built from one
# commit, plus the MariaDB JDBC driver, on the official Docker Hub JRE 17 image. Built by env/up.sh; the build
# context holds only these three jars. Not the project's own hawkbit-runtime/docker images.
ARG JRE_IMAGE
FROM ${JRE_IMAGE}
ARG APP_COMMIT
LABEL mig.env.app-commit="${APP_COMMIT}"
COPY vv8-ui.jar mgmt-server.jar /app/
COPY mariadb-java-client.jar /drivers/
# /artifactrepo: the file artifact store (default path ./artifactrepo, working dir /); a new volume takes this owner
RUN mkdir /artifactrepo && chown 1000:1000 /artifactrepo
ENV HOME=/tmp
WORKDIR /
USER 1000:1000
