# hawkBit Management UI (EXPERIMENTAL!)
The hawkBit Management UI runtime is a standalone spring-boot application with an embedded servlet container. It serves the Vaadin 25 (Vaadin Flow) Management UI of the `hawkbit-ui` module; the module keeps its earlier name `hawkbit-vv8-ui` from the Vaadin 8 UI. It should be started with at least hawkbit-mgmt-server: the two use the same database and communicate via Spring Cloud Bus (RabbitMQ), and hawkbit-mgmt-server creates the database schema (Flyway is disabled in this runtime).

## On your own workstation

### Build

Needs Java 21. From the repository root:

```bash
mvn -pl hawkbit-runtime/hawkbit-vv8-ui,hawkbit-runtime/hawkbit-mgmt-server -am -DskipTests package
```

### Run

```bash
java -jar hawkbit-runtime/hawkbit-vv8-ui/target/hawkbit-vv8-ui-*-SNAPSHOT.jar
```

_(Note: you have to add the JDBC driver also to your class path if you intend to use another database than H2.)_ For example with MySQL or MariaDB, the driver jar in a folder of its own and Spring Boot's `PropertiesLauncher` (package `org.springframework.boot.loader.launch` since Spring Boot 3.2):

```bash
java -cp hawkbit-runtime/hawkbit-vv8-ui/target/hawkbit-vv8-ui-0.4.0-SNAPSHOT.jar \
  -Dspring.profiles.active=mysql -Dloader.path=/path/to/drivers/ \
  org.springframework.boot.loader.launch.PropertiesLauncher
```

hawkbit-mgmt-server starts the same way with its own jar.

Or:

```bash
run org.eclipse.hawkbit.app.vv8ui.Vaadin8UIStart
```

The folder `env/` in the repository root starts this runtime with hawkbit-mgmt-server, MySQL 8.0 and RabbitMQ 3 in Docker with one command (see `env/README.md`).

### Usage
The Management UI can be accessed via http://localhost:8082/UI

The views have their own addresses under `/UI/`, e.g. http://localhost:8082/UI/deployment. Without configured users the login is the default `admin`/`admin` of `application.properties`.

### Production and development mode
The UI runs in Vaadin's production mode. How to run it in development mode from an IDE: `hawkbit-ui/README.md`. How to change its look: `docs/content/guides/customtheme.md`.

### Clustering (Experimental!!!)
The micro-service instances are configured to communicate via Spring Cloud Bus. You could run multiple instances of any micro-service but hawkbit-mgmt-server. Management server run some schedulers which shall not run simultaneously - e.g. auto assignment checker and rollouts executor. To run multiple management server instances you shall do some extensions of hawkbit to ensure that they wont run schedulers simultaneously or you shall configure all instances but one to do not run schedulers!

## Optional Protostuff for Sprign cloud bus
The micro-service instances are configured to communicate via Spring Cloud Bus. Optionally, you could use [Protostuff](https://github.com/protostuff/protostuff) based message payload serialization for improved performance.

**Note**: If Protostuff is enabled it shall be enabled on all microservices!

Add/Uncomment to/in your `application.properties` :

```properties
spring.cloud.stream.bindings.springCloudBusInput.content-type=application/binary+protostuff
spring.cloud.stream.bindings.springCloudBusOutput.content-type=application/binary+protostuff
```

Add to your `pom.xml` :

```xml
<dependency>
  <groupId>io.protostuff</groupId>
  <artifactId>protostuff-core</artifactId>
</dependency>
<dependency>
  <groupId>io.protostuff</groupId>
  <artifactId>protostuff-runtime</artifactId>
</dependency>
```