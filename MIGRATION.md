# hawkBit Migration Guides

## Vaadin 25 migration (Vaadin 25, Spring Boot 4.1, Java 21)

The Management UI and all server programs moved from Vaadin 8, Spring Boot 2.7 and Java 17 to Vaadin 25, Spring Boot 4.1 and Java 21. The hand-off documentation in [`docs/vaadin25-migration/`](docs/vaadin25-migration/) (`handoff.pdf`, or `handoff.md` as source) describes every change with its reason. Read it before you upgrade, in particular:

- renamed configuration properties (`server.servlet.encoding.*`, `server.error.*`, `vaadin.servlet.*`) and the new Spring Boot launcher class names;
- new UI addresses (`/UI/deployment` instead of `/UI/#!deployment`; old bookmarks are forwarded) and changed HTTP status codes;
- the theme is plain CSS (Sass themes no longer apply) and the login footer has a new path;
- container images, the database rehearsal (no schema change), expected startup log lines, Content-Security-Policy, security notes and the cut-over checklist.

## Release 0.2

### Configuration Property changes

- `hawkbit.server.controller._` have changed to `hawkbit.server.ddi._`
- `info.build._` have changed to `hawkbit.server.build._`
- `hawkbit.server.demo._` have changed to `hawkbit.server.ui.demo._`
- `hawkbit.server.email.support` has changed to `hawkbit.server.ui.links.support`
- `hawkbit.server.email.request.account` has changed to `hawkbit.server.ui.links.requestAccount`
- `hawkbit.server.im.login.url` has changed to `hawkbit.server.ui.links.userManagement`

### REST API model changes for clients

- ENTITYPagedList classes have been removed; generic `PagedList` used instead (e.g. `PagedList<TargetRest>` instead of `TargetPagedList`).
- ENTITYsrest classes have been removed; `List<ENTITYrest>` used instead (e.g. `List<TargetRest>` instead of `TargetsRest`)

### Renamed api annotations

- Annotation `org.eclipse.hawkbit.rest.resource.EnableRestResources` has changed to `org.eclipse.hawkbit.mgmt.annotation.EnableMgmtApi`
- Annotation `org.eclipse.hawkbit.ddi.resource.EnableDirectDeviceApi` has changed to `org.eclipse.hawkbit.ddi.annotation.EnableDdiApi`

### Renamed maven modules

- Module `hawkbit-mgmt-api-client` has changed to `hawkbit-example-mgmt-simulator`

## Milestone 0.3.0M6

### Configuration Property changes

- `hawkbit.server.security.dos.maxTargetsPerManualAssignment` has changed to `hawkbit.server.security.dos.maxTargetDistributionSetAssignmentsPerManualAssignment`

## Milestone 0.3.0M7

### Configuration Property changes

- Due to Spring Boot version upgrade (see [spring boot 2.2 deprecations](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-2.2-Release-Notes#deprecations-in-spring-boot-22)) `server.use-forward-headers` has changed to `server.forward-headers-strategy`

## Upgrade from Master Branch (after 0.3.0M6) to 0.3.0M7

Due to changes in the DB migration scripts within PR [#1017](https://github.com/eclipse/hawkbit/pull/1017) the Hawkbit will not start up if one of the following cases is true:

- DB2 database is used
- MSSQL database is used and the `sp_action` table is not empty
- PostgreSql database is used and the `sp_action` table is not empty

The script was fixed with PR [#1061](https://github.com/eclipse/hawkbit/pull/1061).

In case you upgrade from 0.3.0M6 to 0.3.0M7 there is no issue. But if you have built the Hawkbit from the master branch between PR [#1017](https://github.com/eclipse/hawkbit/pull/1017) and PR [#1061](https://github.com/eclipse/hawkbit/pull/1061), use PostgreSQL or MSSQL and upgrade to 0.3.0M7, it will fail at startup with the message: `Validate failed: Migration checksum mismatch for migration version 1.12.16`

This can be fixed by adapting the schema_version table of the database. The checksum field of the entry with the version 1.12.16 has to be changed (mind the minus):

- -1684307461 for MSSQL
- -596342656 for PostgreSql

Example for MSSQL: `UPDATE schema_version SET checksum=-1684307461 WHERE version='1.12.16'`
