---
title: hawkBit Management UI on Vaadin 25
subtitle: Hand-off documentation for the migration from Vaadin 8 / Spring Boot 2.7 / Java 17 to Vaadin 25 / Spring Boot 4.1 / Java 21
kicker: Migration hand-off
version: 1.0 (release candidate)
date: 2026-10-30
status: Release candidate — awaiting acceptance (M3)
audience: Operators, developers and users of the hawkBit Management UI
author: Vaadin migration team
header: hawkBit migration to Vaadin 25 · Hand-off documentation
notice: Release candidate — awaiting acceptance (M3). Milestone M2 passed on 30 October 2026. Written from the migrated code at commit 684b25a: the workflow stage's last commit 1c662d9 (merged into mig-dev as d8e794c on 30 October 2026) plus the small fixes of the hand-off stage, with every decision you made during the migration. The document becomes final when you accept the hand-off.
---

[[TOC]]

# Executive summary {-}

<div class="exec">

> **Release candidate — awaiting acceptance (M3).** This document describes the migrated code at commit `684b25a`, the code put forward for your acceptance. It is the last commit of the workflow stage, `1c662d9`, which was merged into `mig-dev` unchanged (merge commit `d8e794c`) when you signed off the workflow milestone (M2) on 30 October 2026, plus three small fixes of the hand-off stage: the filter panel tooltips (section 3.2), the grid headers with a search (section 3.1) and the timing of one test. It includes every answer you gave during the migration. One known open item is the MySQL release image's driver download (section 5.5). **M2 passed on 30 October 2026; M3, your acceptance of the hand-off, is pending.** The document becomes final when you accept the hand-off.

## Outcome {-}

The hawkBit Management UI and every server program in the build now run on **Vaadin 25.3, Spring Boot 4.1, Jakarta EE 11 and Java 21**, replacing Vaadin 8.14, Spring Boot 2.7 and Java 17. The UI is a server-side Vaadin Flow application with free Vaadin components only: there is no GWT widget build, no Sass theme and no commercial component, and no Vaadin licence key is needed.

The code for your test run is on the M3 branch **`stage/3-handoff`** (the M3 pull request). After your acceptance it merges into **`mig-dev`**, which keeps the migrated version. The original stays on `main`, so the two can be compared. Nothing is merged into `main` as part of the hand-off (your decision, 29 October 2026).

The migration was checked against the original application, with both run side by side:

| Check | Result on the migrated app |
|---|---|
| 31 user workflows (all agreed as critical), replayed on both apps and compared step by step | **31 of 31 pass or show only a difference you accepted**: 25 pass, 6 differ in an accepted way (field error messages under the field: 3; forbidden views answer 404: 3) |
| Backend and UI test suite | Full suite: 1699 tests, 0 failures, 0 errors, 0 skipped, on commit `684b25a`. Continuous integration on `684b25a`: 1672 tests, 0 failures |
| Access matrix: 8 test users × every view, by menu and by address | Identical to the original app |
| HTTP behaviour of the login, sign-out, UI and error addresses and the security headers | No access weakened; each difference fixed or accepted |
| A change made through the REST API appears in an open view | Within 2.2 s on both apps (20 of 20 readings) |
| Continuous integration (build, start, smoke test of every view for 4 users, test suite, 6 browser end-to-end tests) | Green on `684b25a`: 36 of 36 view checks, 1672 tests with 0 failures, 6 of 6 browser tests. Later commits change only documentation and code comments |
| Soak test: 400 sign-in, all-views, sign-out cycles per app, about 2.5 hours each | Memory and threads flat on both apps; the migrated app uses fewer threads (73 against 81). Two framework log errors in 400 sign-outs (section 2.4) |

## What changed {-}

- **For operators:** a Java 21 runtime; several configuration property names (section 2.2); the Spring Boot launcher class name; the container build files; a few HTTP status codes and headers for unusual requests (section 2.3); some new WARN lines at startup (section 5.7). The database schema is **unchanged**: no database migration script was added or changed.
- **For users:** the same views, workflows, texts and permissions. The look is Vaadin's Lumo theme in its compact form with hawkBit's colours. Views have real addresses (`/UI/deployment` instead of `/UI/#!deployment`), and old bookmarks are forwarded. A number of controls behave slightly differently; each is listed in section 3, with your decision.
- **For developers:** the UI code is Vaadin Flow. The theme is plain CSS, and the event bus library is now source code in the app. The Java API has a few changes, the tests run on JUnit 5/6 and Spring 7, and there is a new browser end-to-end test module (section 4).

## Risks {-}

1. **Anonymous Vaadin requests to the login address.** Vaadin answers its own protocol requests on the login addresses with its standard answers, where the original answered 404. You accepted this, and you asked for **a human security review before production** (section 5.9).
2. **Soak test not repeated on the final code.** The soak test ran on an earlier commit of the workflow stage. It was not repeated on the final commit, as you decided. Later changes were covered by the test suite and the workflow runs.
3. **Existing databases.** There is no schema change, but the database migration library moves from Flyway 8.5 to 12.4. A rehearsal on a database written by the original version passed. Repeat it on a copy of your production database first (section 5.6).
4. **Strict Content-Security-Policy.** The UI needs `'unsafe-eval'` in a Content-Security-Policy, as the original did (section 5.8).
5. **MySQL release image:** its driver download stage could not be run, because the MariaDB download host was down; re-check it with one command (section 5.5). The standard release image is verified.
6. **Not verified:** single sign-on (OIDC) and databases other than MySQL 8.0 and the test suite's H2 (as agreed). The Vaadin 24 "simple UI" module is not built by default and has not been ported.
7. **No vulnerability scanning.** The upstream project's nightly vulnerability scan was removed with its workflows, and nothing in CI scans the new dependency set. Run a dependency scan before production (section 5.9).
8. **Pre-existing security weaknesses are kept as they were**, as agreed: default passwords, the open redirect and cross-site sign-out, login throttling that can be bypassed, and a leftover device login on a message thread (section 7.1).

## Still open {-}

No behaviour decision is open: every question put to you during the migration has an answer, and the code follows it. What remains:

1. **Your test run and acceptance of the hand-off (M3)**, on the M3 branch `stage/3-handoff` (the M3 pull request); after your acceptance it merges into `mig-dev`. The workflow milestone (M2) passed on 30 October 2026.
2. **Before production, your own actions:** the security review (section 5.9); a dependency scan of the new stack (section 5.9); the database rehearsal on your data (section 5.6); new passwords for every default account (section 7.1).
3. **Before you distribute the software:** regenerate the third-party dependency list (section 5.10).
4. **Before you use the MySQL release image:** re-check its driver download stage once the MariaDB download host answers (section 5.5).

## Your decisions, in short {-}

Section 2 to 4 give each change with its decision and date. The main answers:

- **Accepted as built:** the new look (Lumo compact, hawkBit's colours, system font); one row height for every grid; field error messages as text under the field; forbidden and unknown views answer HTTP 404; focus starts in the Username field on the login page; the combo-box clear button; the notifications pop-up closing on Esc or a click outside; the bulk upload button; the tenant name as plain text; the upload changes, including two documented exceptions from one Vaadin safeguard for the upload drop area; browser tests in Java with Playwright; release images built from a local jar by default; accessible names added only where the migration had removed one.
- **Restored or added at your request:** the hour/minute/second pop-up of the System Config time fields; the Deployment filter panel with one section always open; the login footer moved out of the old theme folder; upload size limits checked in the browser, so users get the message at once; the filter panel tooltips shown beside the row, letting clicks pass through (30 October 2026).
- **Platform facts, not decisions:** the changes the new platform forces (package names `jakarta.*`, renamed properties, Java API changes) are listed as facts.

</div>

# About this document

## Purpose and reader

This document hands over the migrated hawkBit application. It is written for three readers: **operators** who build, deploy and run it; **developers** who maintain and extend it; and **product owners and users** who need to know what looks or behaves differently. You do not need access to the migration team's working files to use it. Everything it names is either in your repository or explained here.

The source of this document, the build script and the generated HTML and PDF are in the repository folder `docs/vaadin25-migration/`. To rebuild the HTML and PDF after editing the source, run in that folder, with Node.js 22.13 or later: `npm ci` (installs the npm packages pinned in its `package-lock.json` into the folder's own, git-ignored `node_modules`), once per machine `npx playwright install chromium`, then `node build.cjs`. The repository's upgrade guide `MIGRATION.md` points here.

## What was migrated

| | Before (original) | After (migrated) |
|---|---|---|
| Java | 17 | 21 |
| UI framework | Vaadin 8.14.3 with vaadin-spring 3.2.1, Vaadin4Spring add-ons, a GWT widget set and a Valo-based Sass theme | Vaadin 25.3.4 (Flow 25.3.5), free core components, Lumo theme with plain CSS |
| Spring | Spring Boot 2.7.18 (Spring 5.3, Security 5.7), Spring Cloud 2021.0.8 | Spring Boot 4.1.2 (Spring 7.0, Security 7.1), Spring Cloud 2025.1.4 |
| Servlet container | Embedded Tomcat 9 (`javax.*`) | Embedded Tomcat 11 (Jakarta EE 11) |
| Persistence | EclipseLink 2.7.13, Flyway 8.5.13 | EclipseLink 5.0.2, Flyway 12.4.0 |
| JSON | Jackson 2.13 | Jackson 3.1 (REST and Spring Cloud Bus); Jackson 2.21 for device messages over AMQP (DMF) |
| Database verified | MySQL 8.0 (MariaDB JDBC 3.1.4), H2 in tests | MySQL 8.0.46 (MariaDB JDBC 3.5.10), H2 in tests |

**In scope:** the Management UI module (`hawkbit-ui`) and all server programs of the default build: Management server, Management UI runtime (`hawkbit-vv8-ui`, which keeps its old name), the all-in-one Update Server, and the DDI and DMF servers. All of them build, start and run on the new stack.

**Out of scope:** the separate Vaadin 24 "simple UI" (`hawkbit-runtime/hawkbit-simple-ui`). It is no longer part of the default build (section 2.5).

## How the result was checked

The original application was frozen at the commit tagged `baseline-green` on `main`. Both applications were run in the same isolated container setup, with MySQL 8.0, RabbitMQ 3, the same small synthetic data set and the same test users, in production mode and in Chromium. The 31 user workflows were recorded on the original application. They were then replayed on the migrated application and compared step by step: outcome, texts, data and screenshots. A difference was either fixed or put to you as a decision. Only differences you accepted count as "accepted". A final run of all 31 workflows on the workflow stage's last commit, and a re-run on the final code of the five workflows that the hand-off fixes touch, are reported in the final migration report, which is delivered with this document.

## Status words used in the change tables

| Status | Meaning |
|---|---|
| **Agreed** | Decided before the migration work started (planning decisions, 27 October 2026) |
| **Accepted** | You accepted it during the migration, on the date given |
| **Requested** | Built or restored at your request during the migration, on the date given |
| **Platform** | A direct consequence of the agreed target platform. A fact, not a decision (your instruction, 29 October 2026) |
| **Restored** | The migrated app behaves as the original after a fix; listed because the mechanism or a detail changed |
| **Beyond parity** | Better than the original, not asked for; listed so you know |
| **Look only** | A small look difference from the original that came with a fix in the hand-off stage. It was not put to you as a decision; you see it in your test run (M3) |
| **Your action** | Something you or your operators do; the migration did not do it |

# What changed: operators

Only changes that alter how the application is built, deployed, configured or run are listed. Each row gives the old and new value and the reason.

## Runtime and build platform

| Change | Old → new | Why | Status |
|---|---|---|---|
| Java runtime | Java 17 → **Java 21** for every jar and image | Required by Vaadin 25 and Spring Boot 4 | Agreed |
| Platform libraries | Spring Boot 2.7.18 → 4.1.2, Spring Cloud 2021.0.8 → 2025.1.4, Tomcat 9 → 11, EclipseLink 2.7 → 5.0, Flyway 8.5 → 12.4. Every library that Spring Boot manages moves with it | Target platform agreed at planning | Agreed |
| Build download hosts | Maven Central plus `maven.vaadin.com` (Vaadin 8 add-ons) → **Maven Central only**. Packaging a UI runtime runs Vaadin's frontend build, which may download Node.js from `nodejs.org` and npm packages from `registry.npmjs.org` (kept in `~/.vaadin` for later builds) | The Vaadin 8 add-ons are gone; Vaadin 25 has a frontend build step | Platform |
| Removed version pins | Security override pins for Spring Boot 2.7 removed: `snakeyaml` 1.33 → 2.6, `logback` 1.2.13 → 1.5.38, `spring-security-oauth2-jose` 5.8.8 → 7.1.2 (Spring Boot 4's own, newer versions); `jacoco` 0.8.8 → 0.8.11 | The pins held back versions Spring Boot 4 manages; Java 21 needs a newer jacoco | Platform |
| Spring Boot launcher | `org.springframework.boot.loader.PropertiesLauncher` / `JarLauncher` → `org.springframework.boot.loader.launch.PropertiesLauncher` / `...launch.JarLauncher` | Spring Boot 3.2 moved the launcher package | Platform |
| Eclipse licence check (Dash) | Always resolved in the default build → opt-in profile `eclipse-dash` (`-Peclipse-dash`, or `-Ddash.skip=false` as `check-dependencies.sh` passes it) | The plugin lives only on an Eclipse repository; it did nothing for the app | Agreed |

## Configuration you may have to rename

If your own configuration sets any of these properties, rename it. Spring Boot 4 and Vaadin 25 no longer read the old names, and nothing warns you.

| Old name | New name | Set by hawkBit itself |
|---|---|---|
| `server.servlet.encoding.charset`, `.enabled`, `.force` | `spring.servlet.encoding.*` | Yes (renamed in all runtimes) |
| `server.error.*` | `spring.web.error.*` | No |
| `vaadin.servlet.productionMode` | `vaadin.productionMode` | Yes (`true`) |
| `vaadin.servlet.urlMapping` | `vaadin.url-mapping` | Yes (`/UI/*`) |
| `vaadin.servlet.heartbeatInterval` | `vaadin.heartbeatInterval` | Yes (60 s) |
| `vaadin.servlet.closeIdleSessions` | `vaadin.closeIdleSessions` | Yes (`false`) |
| (new) | `spring.jackson.use-jackson2-defaults=true` in all five runtimes: keeps the JSON format of Jackson 2 | Yes |

Further notes:
- Custom `spring.jackson.*` features now map to Jackson 3 feature names; check yours against the Spring Boot 4 documentation.
- The UI's auto-configuration is registered in `META-INF/spring/...AutoConfiguration.imports` instead of `spring.factories` (Spring Boot 4 reads only the imports file).
- The upgrade guide `MIGRATION.md` in the repository opens with a short section for this upgrade that points to this document.

## HTTP behaviour, addresses and headers

These matter if you run proxies, monitoring, security scanners or API clients that look at status codes or headers.

| Change | Old → new | Why | Status |
|---|---|---|---|
| UI addresses | `/UI/#!deployment` (and `#!rollout`, `#!targetFilters`, `#!distributions`, `#!spUpload`, `#!spSystemConfig`) → `/UI/deployment` etc.; deep link `/UI/deployment/target=<id>`. Old `#!` addresses are forwarded with one page load. `/UI/login`, `/UI` and `/UI/logout` are unchanged | Vaadin 25 router; agreed to keep bookmarks working | Agreed |
| Unknown address or forbidden view under `/UI/` | The page answered 200 (the old error view had no status of its own) → **HTTP 404**, with the same error view and texts inside the app frame ("No such view: …", "No access to view: …" with the "Access denied" message). Browsers log the 404 in their console | The new app has real addresses | Accepted (28 Oct 2026) |
| Signed-in `/UI` without trailing slash | 200 → one redirect to `/UI/` | Vaadin's servlet redirects before application code runs | Accepted (28 Oct 2026) |
| `X-XSS-Protection` header on every response | `1; mode=block` → `0` | Spring Security 7 default. Current browsers no longer have the XSS filter this header controlled, and OWASP advises against enabling it | Accepted (27 Oct 2026) |
| Requests the security firewall rejects (disallowed host name, malformed path) | 500 with a stack trace → **400** | Spring Security 7's rejected-request handling | Accepted (28 Oct 2026) |
| REST, device and download addresses ending in `//`, or a single trailing slash on device paths | 500 with a stack trace → a normal 401, 403 or 404 (or the same answer as without the slash) | Spring 7 path handling | Accepted (28 Oct 2026) |
| CORS preflight (`OPTIONS`) to an address without CORS settings | 403 → 200 without CORS headers. Browsers block the cross-origin request in both cases. Error, not-found and `OPTIONS` answers no longer carry `Vary: Origin, …`; an anonymous preflight to the Management API answers 401 instead of 403 | Spring 7 CORS handling | Accepted (28 Oct 2026) |
| Full artifact downloads | ETag sent as the bare hash → quoted (`"<sha1>"`, the standard form) | Spring 7 | Accepted (28 Oct 2026) |
| Trailing slash on REST addresses (`/rest/v1/targets/`) | Same answer as without the slash, as before. It is now done by a servlet filter for `/rest/**`, `/system/admin/**`, `/api/v1/downloadserver/**`, `/*/controller/v1/**`. **If you deploy under a servlet context path, these trailing-slash addresses answer 404** | Spring 7 removed trailing-slash matching | Restored |
| Vaadin's own static files | These load without login: `/UI/sw.js`, `/UI/offline.html`, `/UI/styles.css`, `/UI/themes/**`, `/UI/assets/**`, `/aura/**`, `/lumo/**`, `/favicon.ico`. Vaadin's internal requests from a signed-out browser answer **401** instead of a redirect to `/UI/login`. No data is exposed | Vaadin's security configuration for Spring Security 7 | Platform |
| Anonymous Vaadin protocol requests (`v-r=uidl`, `heartbeat`, `push`, `i18n`) to `/UI/login`, `/UI/login/` and `/UI/login/...` | 404 (or the login page) → Vaadin's standard no-session answers (200 JSON "session expired", 403 or 404), no cookie set (the old 404 set a session cookie). Browsers never send these; only crafted requests do | Vaadin handles its own protocol requests | Accepted (28 Oct 2026; the login address itself confirmed 29 Oct 2026). **Security review before production** (section 5.9) |
| Content-Security-Policy | None by default, as before. A policy you set must allow `'unsafe-eval'`, as for the original (section 5.8) | Vaadin runs scripts sent by the server | Platform |
| Page cache headers | `/UI/login` and the UI page send the original `Cache-Control: no-cache`, `Pragma`, `Expires` again. The new 404 UI pages send them too | Parity | Restored |
| Default profile picture address | `/UI/VAADIN/themes/hawkbit/images/profile-pic-57px.jpg` → `/UI/VAADIN/hawkbit/images/profile-pic-57px.jpg` (same image) | The old theme folder is not served by Vaadin 25 | Platform |
| JSON error responses | Keep `;charset=UTF-8` as before, through one new hawkBit auto-configuration (16 in total now) | Spring 6.2+ resets the content type before error handling | Restored |

## Messaging and logs

| Change | Old → new | Why | Status |
|---|---|---|---|
| Spring Cloud Bus messages between the server programs | Same payloads, exchange and routing key; the AMQP header `target-protocol: amqp` is **no longer sent**. *Known difference for release notes*: anyone who reads this header in their own code will not find it | Spring Cloud Stream 5 no longer sets it | Accepted (28 Oct 2026) |
| JSON of the REST API and Bus events | Written and read by Jackson 3 instead of Jackson 2. 143 Management API requests and 35 request-body cases give the same status and bytes as the original after normalising timestamps, tokens and ids | Spring 7 / HATEOAS 3 support only Jackson 3 | Restored |
| Device messages (DMF over AMQP) | The message converter trusts the types of two hawkBit packages (`org.eclipse.hawkbit.dmf.json.model`, `org.eclipse.hawkbit.security`) instead of all packages. Same behaviour for every hawkBit message. **If you extend the DMF API with types in other packages, add them** | Spring AMQP 4 changed the default; the narrower list keeps its safeguard | Beyond parity |
| Startup log | New WARN lines at startup that the original did not log; harmless. Listed in section 5.7 | Spring 7, Spring Security 7, springdoc 3 | Platform |
| Server log, validation | One Hibernate Validator WARN (`HV000272`) on the first validated use of two generic create/update methods after each start; harmless | The remaining annotation form is in shared interfaces | Accepted (28 Oct 2026) |
| Server log, sign-out | A heartbeat or push request of a page that is being signed out can meet the framework's "Session already invalidated" race (Vaadin 8 has the same race). Where it reaches hawkBit's UI error handler it is logged as **one WARN** and the user sees nothing. Where Vaadin's own code meets it, it can still log an ERROR line and answer that request with 500, as in Vaadin 8. Seen 2 times in 400 sign-outs in the soak test; no user effect | The handler failed with a second error before; the race itself is in the framework | Platform |
| Refused artifact uploads (duplicate, over the size limit) | Above 2 MB the connection was reset → a file over the size limit is now refused in the browser before it is sent (section 3.3). For any other refusal that reaches the server, the server reads and discards up to 100 MB / 60 s of the rest of the upload, so the browser gets a normal answer and the status window shows the reason. Above that bound the connection ends as before | Same message for users without a browser error, bounded server time | Requested (29 Oct 2026) / Platform |

## Build and repository

| Change | Old → new | Why | Status |
|---|---|---|---|
| Vaadin 24 simple UI (`hawkbit-runtime/hawkbit-simple-ui`) | Part of the default root build → opt-in profile `simple-ui`. The module is unchanged and **does not build** on the new stack until someone ports it. A plain `mvn install` builds the other 36 modules | Out of scope, and it depends on modules that moved to Java 21 / Spring Boot 4 | Accepted (28 Oct 2026) |
| Container build files (`hawkbit-runtime/docker/docker_build/`) | Base image `eclipse-temurin:17.0.9_9-jre-alpine` → `21.0.12.1_1-jre-alpine`; layer extraction `-Djarmode=layertools extract` → `-Djarmode=tools extract --layers --launcher`; new launcher class. Users, ports, volumes, JVM flags and profiles unchanged | The migrated jars are Java 21 / Spring Boot 4 | Accepted (28 Oct 2026) |
| Release flavours `Dockerfile`, `Dockerfile-mysql` | Downloaded the released `0.3.0` jar by default → **take the jar from the build context by default** (`HAWKBIT_JAR_SOURCE=local`). The signed download stays an option (`--build-arg HAWKBIT_JAR_SOURCE=download`). A bare `docker build` without a jar now fails at once | `0.3.0` is not a migrated release and is not expected to start on the new image layout; there is no migrated release yet | Accepted (29 Oct 2026) |
| Upstream automation | The upstream project's GitHub Actions (nightly vulnerability scan, stale-issue bot, first-PR greeting) and `.trivyignore` with two CVE acceptances removed. The repository's own CI replaces them (section 6.3). The upstream CircleCI and Azure files are still there but inactive, and they still name JDK 17 | Not part of the app; the migration has its own CI | Agreed |
| Licence of copied code | The Vaadin4Spring event bus (Apache 2.0) is now source inside `hawkbit-ui` (section 4.2), with its licence text in `licenses/APACHE-2.0.txt` and a line in `.3rd-party/DEPENDENCIES` | No Vaadin 25 version of the add-on exists | Agreed |
| `.3rd-party/DEPENDENCIES` | Still lists the old stack (Vaadin 8, Spring 5.3, …); only the event bus line was edited. **Regenerate it before you distribute** (section 5.10). It was not regenerated during the migration, because the tool contacts Eclipse Foundation services and the migration agreed to make no contact with the upstream project | It is generated output | Your action |
| Hand-off documents | New folder `docs/vaadin25-migration/` with this document (source, HTML, PDF, build script and its pinned npm packages in `package.json` and `package-lock.json`); `MIGRATION.md` points to it | Hand-off | Agreed |

# What changed: users

The views, menus, permissions, texts and workflows are the same. Section 3.1 covers the look. Sections 3.2 and 3.3 list each control that behaves differently. The side-by-side walkthrough delivered with these docs (section 8) shows every workflow in both versions.

## Look

| Change | Old → new | Why | Status |
|---|---|---|---|
| Overall theme | Valo-based hawkBit theme → Vaadin's **Lumo theme, compact preset**, with hawkBit's colours: primary `#551f62`, error `#ed473b`, success `#2c9720`, background `#fafafa`, dark menu. Text 14 px in Open Sans if the user has it installed, else the system sans-serif font (old: 16 px Open Sans light, shipped with the app). Controls 32 px high (old 37 px) | Vaadin 25 has no Valo; agreed visual target is structural parity | Accepted (29 Oct 2026) |
| Shell and login | As before: 70 px header band with the hawkBit logo, 203 px menu (icons only below 1100 px window width), centred login form, red `*` on required fields, no password reveal button | Structural parity | Accepted (29 Oct 2026, with the look) |
| Grids | Rows 31 px as before, solid purple selected row with white text, by one global rule for every grid | Old theme used one row-height value for all grids | Accepted (29 Oct 2026) |
| Selection column | Multi-select grids show a **checkbox column**. Click, Ctrl/Cmd+click, Shift+click, Ctrl/Cmd+A and keys select as before (Ctrl/Shift ranges limited to 1000 rows, as before; ticking checkboxes has no such limit) | Vaadin 25's multi-select grid shows it; hiding it needs non-public API | Accepted (28 Oct 2026) |
| Column visibility and headers | Columns are hidden and shown from a menu button at the end of the grid header (one extra narrow column), instead of Vaadin 8's column toggle. Grid header rows are always shown. Captions of grouped icon columns sit in the first column's header cell | Vaadin 25 Grid has no column menu or header hiding | Platform |
| Grid headers with a search (Targets, Distributions, Software Modules, Rollouts, Target Filters, Upload) | Icon buttons about 22 px apart, search field about 100 px wide → icon buttons **slightly narrower** than Vaadin 25's default (about 25 px instead of 33 px), and the open search field **shrinks to the room** the caption and the icons leave, with a small gap to the caption: about 48-57 px wide in a 1024 px window (original 68-80 px). Below 1024 px it can become too narrow to read the typed text (section 9) | At Vaadin 25's default sizes the open search field pushed the icons out of the header, over the caption of the panel beside it; fixed in the hand-off stage | Look only (fixed 30 Oct 2026) |
| Detail tabs, status colours, rollout chart | Framed purple tabs over a bordered box, 116 px high with scrolling; the old status colours and spinners; rollout "Detail status" as a coloured bar; groups pie chart drawn by the server (SVG) with the old slice colours | Structural parity; the GWT chart was dropped | Agreed / Accepted (29 Oct 2026, with the look) |
| Window titles | Windows are Vaadin dialogs with the same titles, buttons, Esc and modality, and a close button in the header. Titles keep the old prefixes ("Metadata of …", "Activate automatic confirmations for …", "Artifact Details of …"); the name after the prefix is no longer bold | Vaadin 25 has no Window component | Platform |
| Auto-confirmation button | Toggle-switch icon → checked / unchecked box icon | FontAwesome is not shipped; Vaadin icons have no toggle switch | Agreed |

## Behaviour of controls

| Change | Old → new | Why | Status |
|---|---|---|---|
| Field validation errors | Red "!" next to the field, message as a tooltip → **message as text under the field**. Same texts | Vaadin 25 standard presentation; the old one would need custom code in every form | Accepted (29 Oct 2026) |
| Pop-up messages | Bottom right, newest on top → bottom end, **newest at the bottom**; each still closes after 3 s | Vaadin 25 notifications | Agreed |
| Tag and type colour | Vaadin 8 colour pop-up (gradient, preview, RGB sliders) → **the browser's own colour picker**; stored format unchanged | No Vaadin 25 colour picker | Agreed |
| Combo boxes that allowed an empty choice | Empty first item → a clear (x) button in the field; same empty result | Vaadin 25 ComboBox has no empty item | Accepted (29 Oct 2026) |
| Notifications (bell) pop-up | Closes when it loses focus, opens below the button → closes on Esc or a click outside it, opens 40 px below the click point; unread counter is a separate element | Vaadin 25 dialogs have no focus-lost event | Accepted (29 Oct 2026) |
| Deployment filter panel (Simple / Custom / Target type) | Exactly one section is open at all times, as before (an earlier migration step allowed all closed; this was restored) | Parity | Restored (confirmed 29 Oct 2026) |
| Tooltips of the filter panel buttons (tags, types and custom filters) | Shown at the pointer, below and to the right of it → shown **to the right of the row**, beside the list, and **clicks pass through the tooltip**: a click on the next row, or on the grid beside the list, lands while the tooltip is still shown. The buttons themselves are unchanged | Vaadin 25 tooltips open below their button and take the pointer, so the tooltip covered the next row and a click there missed; fixed in the hand-off stage. The click-through rule styles a part of Vaadin's tooltip: re-check it after a Vaadin upgrade | Requested (30 Oct 2026) |
| Login page | Keyboard focus starts in **Username** (old: the disabled Sign in button) | Restoring the old focus needed a fragile workaround | Accepted (29 Oct 2026) |
| System Config polling and overdue times | Time field with an hour/minute/second pop-up → an `HH:mm:ss` text field (same parsing, limits and message) with a button at its start that opens the **hour/minute/second pop-up** (three drop-downs; a choice applies at once and is held to the minimum and maximum as before; Esc, a click outside or Tab out of the last drop-down closes it). The pop-up no longer shows today's date above the drop-downs; the caption is at the start of the row | No Vaadin 25 time field with a free format; the pop-up was rebuilt at your request | Requested (29 Oct 2026) |
| Forced update time, rollout start time | One date-time field with a calendar pop-up → a date picker plus a time picker; same value. The year shows with four digits | No Vaadin 25 DateTimeField equivalent | Platform |
| Maintenance window time zone | The list can be filtered by typing; same choices and default | Vaadin 25 ComboBox accepts typing | Platform |
| Radio choices with explanations | The explanation tooltip shows when hovering the choice's text (old: the whole choice, including the circle) | No per-item description in Vaadin 25 radio groups | Platform |
| Target filter query suggestions | Same suggestions and keys (Up/Down wrap, Enter inserts, Esc closes) in a pop-over list; it also closes on a click outside; plain text instead of HTML | The GWT widget was dropped | Agreed |
| Tooltips | HTML tooltips in rollout and filter grids are plain text, one line per status. Icon and button tooltips in grids show over the whole cell, also for disabled buttons. System Config Save and Undo show their tooltips while disabled, as before | Vaadin 25 tooltips are text only | Platform |
| Tenant name in the user menu | Shown as plain text (it was inserted as HTML, unescaped). No change for normal names such as `DEFAULT` | Safer; closes a pre-existing weakness | Accepted (29 Oct 2026) |
| Dates and times | Shown in the browser's time zone as before (zone still guessed from the offset, as before). The page reloads once when a page showed dates before the browser's zone was known: only on the first page of a session that did not start at the login page (for example single sign-on entry), or after the browser's zone changed | Vaadin 25 renders before it knows the browser's zone | Platform |
| A tab left on an error page while signed out | After signing in in another tab, its next navigation reloads the page once | Closes a permission gap found during the migration | Platform |
| Accessible names of icon-only buttons | Restored only where the migration removed one (System Config Save and Undo). Naming every icon-only button is follow-up work (section 9) | Parity first | Accepted (29 Oct 2026) |
| Target status after an auto-assignment | Updates live in the target list (old: stayed "unknown" until the view was reopened) | Side effect of the Vaadin 25 grid refresh | Beyond parity |

## Uploads

| Change | Old → new | Why | Status |
|---|---|---|---|
| Dropping several artifact files | Uploaded and checked **one by one**. A later file attaches to the software module selected when its upload starts (old: all went to the module selected at the drop). Files may be listed in arrival order, not drop order | Vaadin 25's drop zone gives no module at drop time | Accepted (28 Oct 2026) |
| Upload status window and "Clear file upload queue" prompt | Appear when a file's upload request reaches the server, not at the drop. They must still appear once the request starts (your condition, checked in the workflow runs) | No client-side "upload start" event in Vaadin 25.3 | Accepted with condition (28 Oct 2026) |
| One dropped file held back by a slow network, user leaves Upload first | Old: the prompt asked first. New: **no prompt, and the file is not uploaded, with no message**. Normal transfers and multi-file drops are not affected | Same cause as the row above | Accepted (29 Oct 2026) |
| Files over the size limit | Sent to the server and refused there → **refused in the browser before sending**, and listed in the Upload status window with the server's message ("Maximum artifact size (…) exceeded"), in about 0.1 s. The limit is the smaller of `spring.servlet.multipart.max-file-size` and `hawkbit.server.security.dos.max-artifact-size`. The server checks stay; the total storage quota is checked by the server only | You asked for immediate feedback | Requested (29 Oct 2026) |
| Drop area while the status window is open | The drop area accepts its own uploads while the (modal) status window is open, and its drop listener lists files that the browser refused for size in that window. For these two, Vaadin's "inert behind a modal window" safeguard is switched off; session, attachment, visibility and enabled checks still apply | Otherwise the 2nd and later files of a drop were refused, and refused files got no message | Accepted (upload: 29 Oct 2026; drop listener: 30 Oct 2026) |
| Bulk target upload (CSV) | Upload button with one file and no drag and drop. The file name and progress show under the button while it uploads. A file not ending in `.csv` is refused with the same message | Vaadin 25 Upload | Accepted (29 Oct 2026) |
| Upload button in the Upload view | Vaadin 25 upload button, one file per click; the drop area keeps its content | Vaadin 25 Upload | Accepted (28 Oct 2026) |

# What changed: developers and integrators

## APIs

| Change | Old → new | Why | Status |
|---|---|---|---|
| REST error body for validation failures (HTTP 400, `hawkbit.server.error.repo.constraintViolation`) | `exceptionClass` = `javax.validation.ConstraintViolationException` / `ValidationException` → `jakarta.validation.…`. Message text unchanged. **Clients that match on `exceptionClass` instead of `errorCode` must change** | Jakarta EE package rename | Platform |
| Java API: `RolloutManagement.validateTargetsInGroups` | Returns `CompletableFuture` instead of `ListenableFuture` | Spring 7 removed `ListenableFuture` | Platform |
| Java API: management interfaces | `@Valid` on 9 collection parameters moved to the element type (`Collection<@Valid TargetCreate>`); validation unchanged | Hibernate Validator 9 | Platform |
| DDI API interface (`hawkbit-ddi-api`) | `@NotEmpty` removed from two path variables and `@Valid` from one REST body list. Requests accepted and rejected are unchanged; the OpenAPI docs lose `minLength: 1` on those parameters | Spring 6.1+ would now validate them and fail | Platform |
| Repositories through the access-control wrapper | The 4 query methods Spring Data JPA 4 adds are not supported through the wrapper (they would bypass access rules) | Safety | Platform |
| Auto-configuration | Registered in `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` instead of `spring.factories`; 16 hawkBit auto-configurations (one new, for the JSON error charset) | Spring Boot 4 | Platform |
| New runtime dependencies | `spring-security-access` (legacy method security kept for the back end); `spring-boot-hateoas` (restores `application/json` handling of the REST API) | Spring Security 7 and Spring Boot 4 module split | Platform |
| Own runtimes built on the hawkBit starters | Need an error view with `@ParentLayout` to show the app frame around "No such view" (each hawkBit runtime has a `MyErrorView`) | Vaadin 25 routing | Platform |
| Theme constant | `HawkbitTheme.THEME_NAME` removed; code that refers to it no longer compiles | There is no named theme any more | Requested (29 Oct 2026) |

## UI code, theme and security context

| Change | Old → new | Why | Status |
|---|---|---|---|
| UI framework | Vaadin 8 server components with a GWT widget set and SuperDevMode → Vaadin Flow 25, all logic on the server, nothing client-side to compile. Debug with a Java debugger; production mode by default (`vaadin.productionMode=true`) | Target platform | Agreed |
| Development mode | Needs `com.vaadin:vaadin-dev` added while developing, an IDE class path (not the packaged jar) with the module folder as working directory, and `-Dvaadin.productionMode=false`. Use `vaadin.devmode.hostsAllowed` for access from other addresses. Described in `hawkbit-ui/README.md` | Vaadin 25 | Platform |
| Theme customisation | Vaadin 8 Sass theme in `VAADIN/themes/<theme>/` (`hawkbitvariables.scss`) → **plain CSS**: the app shell loads Lumo, Lumo compact and `VAADIN/hawkbit/hawkbit.css` with `@StyleSheet`; a custom look is one more style sheet after them. **Existing Sass themes no longer apply**; the upstream custom-theme example does not work. Guide: `docs/content/guides/customtheme.md` | No Sass themes in Vaadin 25 | Platform |
| Login footer | Read from `VAADIN/themes/hawkbit/layouts/footer.html` → **`META-INF/resources/VAADIN/hawkbit/footer.html`** on the class path (constant `AbstractHawkbitLoginUI.FOOTER_RESOURCE`). **A custom footer at the old path is no longer read: move it.** Described in the Custom Theme guide | The old theme folder is gone | Requested (29 Oct 2026) |
| Vaadin 8 theme sources | 27 SCSS files, 2 duplicate images, 2 `.gitignore` files, `dynamicStylesheet.js`, the whole `VAADIN/themes` folder (with a duplicate `favicon.ico`), the scss-lint rule in `CONTRIBUTING.md`, dead SCSS and widget-set entries in the root `pom.xml`, and comments citing SCSS or Valo removed or updated | They looked like a theme to edit but nothing reads them | Requested (29 Oct 2026) |
| Event bus | The Vaadin4Spring event bus 2.0.0 (Apache 2.0) is copied into `hawkbit-ui` (package `org.vaadin.spring.events`); same API, adapted to Jakarta and Vaadin Flow scopes. The copied files are excluded from the project's licence-header check | No Vaadin 25 version exists | Agreed |
| Security context in the UI | The UI installs its own `SecurityContextHolderStrategy` (`ThreadFirstSecurityContextHolderStrategy`): a context set on the thread (system code, tenant switches) wins over the Vaadin session's user, as with the old holder. hawkBit's executors (`asyncExecutor`, `uiExecutor`, the task scheduler) run tasks with the submitter's user and tenant again. No access gained or lost. Tests prove that background work runs as the submitting user and tenant (9 paths, 8 negative controls). **Re-check both after upgrading Spring Security or vaadin-spring** | Vaadin's own strategy ignored hawkBit's "run as system"; Spring Security 6+ executors captured the strategy too early | Restored |
| Method security in the UI | `@EnableGlobalMethodSecurity` → `@EnableMethodSecurity` with the same switches | Spring Security 7 | Platform |
| Upload drop area (`UploadDropAreaLayout`) | Two documented opt-outs from Vaadin's modal-inert safeguard: the upload handler (`isAllowInert()`) and the drop listener (`allowInert()`, which carries only file names and sizes). One method each, reversible. **Re-check both after a Vaadin upgrade**; if Vaadin's upload manager later lets its own reject listener run behind a modal window, the drop listener can go | See section 3.3 | Accepted (29 and 30 Oct 2026) |
| UI error handler | The "Session already invalidated" race during sign-out is logged as one WARN, without a notification (section 2.4) | The handler failed with a NullPointerException there before | Platform |
| Filter panel button tooltips | Placed at the end of the button (`AbstractFilterButtons.placeTooltipBesideRow`, used by every filter list and the custom filter list), and made to let the pointer through by one rule in `hawkbit.css` on the tooltip's exported `overlay` part, for buttons with the class `button-tag-no-border`. **Re-check after a Vaadin upgrade** that the part keeps its name; if it does not, the tooltip takes clicks again | See section 3.2 | Requested (30 Oct 2026) |

## Tests, CI and development environment

| Change | Old → new | Why | Status |
|---|---|---|---|
| Test code | Backend and UI tests ported to Spring Boot 4, Spring 7, Mockito 5, Jackson 3, JUnit 5/6 and the Stream 5 test binder; no assertion removed. Deliberately changed expectations: a rejected host name is HTTP 400 (not an exception), `CorsTest` expects 200 for a preflight without CORS settings, one download test accepts the quoted ETag. New tests for the duration pop-up, the upload size limit, the error handler, the login footer and user and tenant isolation of background work | Framework change; accepted behaviours above | Platform |
| Build detail | `hawkbit-repository-jpa` empties `target/generated-sources/apt` before EclipseLink's model generator runs (otherwise rebuilds without `clean` failed) | EclipseLink plugin 4.0.2 | Platform |
| Browser end-to-end tests (new) | New module `hawkbit-runtime/hawkbit-vv8-ui-e2e`: Playwright for Java 1.63.0, JUnit 5, failsafe, in the opt-in profile `-Pe2e`. Six tests: sign in and sign out; wrong login; target tags; distribution set rules; software module upload; rollout lifecycle. `env/e2e.sh` runs them against a fresh copy; CI job `e2e`. The tests rely on six non-public Vaadin details that must be checked after a Vaadin update (`env/README.md`). Test upload files are excluded from the licence-header check | Workflows that pass become tests that ship with the app | Accepted (29 Oct 2026) |
| Reproducible environment (new) | Folder `env/`: one command (`env/up.sh`) starts an isolated copy (UI runtime + Management server + MySQL 8.0 + RabbitMQ 3, images pinned by digest, no internet for the app) with a small synthetic data set; `reset.sh`, `down.sh` | Repeatable runs for testing and CI | Agreed |
| CI (new) | `.github/workflows/ci.yml` with notes in `.github/CI.md` (section 6.3) | Every commit is built, smoke-tested and tested | Agreed |
| Dev container | The Java formatter setting points to the repository's own `eclipse_codeformatter.xml` instead of downloading it from the upstream project. Note: the dev container still uses Java version 8 (pre-existing; follow-up) | No contact with the upstream project | Agreed |

## Notes for UI developers

**Window forms move a field's caption.** The windows that create or edit targets, tags, distribution sets, software modules and the bulk upload and auto-confirmation windows lay out their fields with `WindowFormLayout` (`hawkbit-ui`, package `org.eclipse.hawkbit.ui.common.builder`). It shows the caption to the left of the field, as Vaadin 8's form layout did. To do that, `add(...)` and `addComponentAtIndex(...)` wrap each field that has a label in a form item:

- the field's label text moves into the form item's label, and the field's own label is cleared: **`field.getLabel()` returns `null` after `add`**;
- the text stays on the field as its accessible name, so screen readers still announce it;
- checkboxes, and fields without a label text, are added unchanged.

How to work with it:

- **Read** the caption of a field after `add` with `field.getAriaLabel()` (an `Optional<String>`), not with `getLabel()`.
- **Set** the caption with `field.setLabel("…")` before you add the field. Calling `setLabel` after `add` shows the text a second time, above the field.
- **Change** the caption of a field that is already in the form: take its form item out and add the field again, at the same position.

```java
// the form item around the field, and its position in the form
Component item = field.getParent().orElseThrow();
int index = form.getElement().indexOfChild(item.getElement());
form.remove(item);
field.setLabel("New caption");
form.addComponentAtIndex(index, field);   // wraps the field in a new form item
```

- **Tests** find a field by its id or its accessible name, not by its label.

**Scripts sent from the server.** Three UI features run a small script in the browser through Vaadin's `executeJs`: the forward of old `#!` addresses (`AbstractHawkbitUI`), the focus-on-open helper (`FocusOnShow`) and the style rules for software module type colours (`SmTypeCssStylesHandler`). Section 5.8 says what a Content-Security-Policy needs for them.

# Environment and deployment

## Requirements

| What | Version | Notes |
|---|---|---|
| Java runtime | **21** | Every jar and image. Images use `eclipse-temurin:21.0.12.1_1-jre-alpine` (release files) or `eclipse-temurin:21-jre` (`env/`) |
| Build | JDK 21, Maven 3.9 (tested with 3.9.16) | Downloads from Maven Central, `nodejs.org` and `registry.npmjs.org` |
| Database | MySQL 8.0 (verified with 8.0.46 and MariaDB Connector/J 3.5.10); H2 for tests and evaluation | Other databases in the README's table were not tested on this version |
| Message broker | RabbitMQ 3 (verified with 3.13.7) | Needed for DMF and for the Spring Cloud Bus between separate server programs |
| Browser for users | A current evergreen browser | Verified with Chromium only, as agreed |

## Build

From the repository root (the default build leaves out the Vaadin 24 simple UI):

```bash
mvn clean install                       # all 36 modules, with tests
mvn clean install -DskipTests           # jars only
# only the UI runtime and the Management server:
mvn -pl hawkbit-runtime/hawkbit-vv8-ui,hawkbit-runtime/hawkbit-mgmt-server -am -DskipTests package
```

Packaging a UI runtime runs Vaadin's frontend build. As long as the UI has no custom frontend files, it uses Vaadin's pre-built production bundle (the log says "A production mode bundle build is not needed"). It may still download Node.js once into `~/.vaadin`.

## Run

**All in one (Update Server, embedded H2 for evaluation):**

```bash
java -jar hawkbit-runtime/hawkbit-update-server/target/hawkbit-update-server-0.4.0-SNAPSHOT.jar
# UI: http://localhost:8080/UI     Management API: http://localhost:8080/rest/v1
```

**Separate services (Management server + Management UI) with MySQL and RabbitMQ.** The Management server creates and migrates the schema; Flyway is switched off in the UI runtime. Put the JDBC driver in a folder of its own and start each jar with the Spring Boot 3.2+ launcher:

```bash
java -cp hawkbit-runtime/hawkbit-mgmt-server/target/hawkbit-mgmt-server-0.4.0-SNAPSHOT.jar \
  -Dspring.profiles.active=mysql -Dloader.path=/path/to/drivers/ \
  org.springframework.boot.loader.launch.PropertiesLauncher
java -cp hawkbit-runtime/hawkbit-vv8-ui/target/hawkbit-vv8-ui-0.4.0-SNAPSHOT.jar \
  -Dspring.profiles.active=mysql -Dloader.path=/path/to/drivers/ \
  org.springframework.boot.loader.launch.PropertiesLauncher
# UI: http://localhost:8082/UI/
```

Configure the database, RabbitMQ and users as before (`spring.datasource.*`, `spring.rabbitmq.*`, `hawkbit.server.im.users[N].*`), with the renamed properties of section 2.2. **Change the shipped default passwords** before any real use (section 7.1). Users' passwords belong in a protected configuration file or secret store, not in the repository.

The UI runs in Vaadin's production mode; the start log says "Vaadin is running in production mode."

## Local test copy (`env/`)

`env/up.sh` builds an image of the current commit and starts an isolated copy: UI runtime, Management server, MySQL 8.0 and RabbitMQ 3 on an internal Docker network, seeded with a small synthetic data set. The UI is at `http://127.0.0.1:$APP_ENV_PORT/UI/`. `env/reset.sh` returns it to the seed state in about 10 s, and `env/down.sh` removes it. Needs bash 4.4+, git, curl, GNU `timeout` and `sha256sum`, and Docker on the same host. About 2.5 GB of memory per copy. Full description: `env/README.md`. This is a test aid, not a production deployment.

## Container images

Build the images from this source. **Do not pull `hawkbit/*` images from Docker Hub**: they are the Eclipse hawkBit project's own releases with the old Vaadin 8 UI, and Docker pulls them whenever no local image of that name exists.

Development images of all five programs, from your local Maven repository (`hawkbit-runtime/docker/docker_build/`):

```bash
mvn clean install                         # repository root, Java 21
cd hawkbit-runtime/docker/docker_build
./build_all_dev.sh                        # hawkbit/<app>:0.4.0-SNAPSHOT and :latest
./build_all_dev.sh -f mysql               # ...-mysql flavour (needs the MariaDB driver in ~/.m2)
```

The mysql flavour uses MariaDB driver 3.1.4 by default; the migrated app was verified with **3.5.10**. Pass `--build-arg MARIADB_DRIVER_VERSION=3.5.10` when you build a mysql image by hand. `build_all_dev.sh` does not pass a version, so it uses 3.1.4.

Release flavour from a jar you built (the default):

```bash
cp <your build>/hawkbit-update-server-<version>.jar hawkbit-update-server.jar
docker build -t hawkbit_update_server:<version> . -f Dockerfile
docker build -t hawkbit_update_server:<version>-mysql . -f Dockerfile-mysql
```

The local jar is not checked: whoever builds the image owns the jar's provenance. `--build-arg HAWKBIT_JAR_SOURCE=download` downloads and verifies a released jar instead. The default `HAWKBIT_VERSION` 0.3.0 is not a migrated release and is not expected to work.

What was proven:
- A development image of the Management server starts.
- A standard release image of the Update Server starts.
- A mysql release image of the Update Server starts on MySQL 8.0 with RabbitMQ 3, with no errors, with the driver supplied locally.

**Known open item: the MySQL flavour's driver download.** The standard release image is verified. The `driver` stage of `Dockerfile-mysql`, which downloads the MariaDB driver and checks its signature, could not be run: the MariaDB download host (`downloads.mariadb.com`) answered HTTP 522 for the driver and its signature file on every try, on 29 and again on 30 October 2026. The steps before the download pass; the mysql image itself starts when the driver is supplied locally (above). Re-check the stage once the host answers, in `hawkbit-runtime/docker/docker_build/`:

```bash
docker build --target driver -f Dockerfile-mysql .
```

The compose files in `hawkbit-runtime/docker/` (monolith and micro-services with MySQL) use the image names above. See `hawkbit-runtime/docker/README.md` for the access table.

## Database migration

**No database migration script is part of this migration.** The Flyway scripts under `hawkbit-repository/hawkbit-repository-jpa/src/main/resources/db/migration/` (H2, MySQL, PostgreSQL, SQL Server, DB2) are byte-for-byte the same as in the original app. The schema the new EclipseLink 5 maps is the existing one.

What changes is the library that applies the scripts:
- Flyway 8.5.13 becomes Flyway 12.4.0, through Spring Boot's Flyway starter. MySQL support stays in `flyway-mysql`.
- The schema history table is still `schema_version`.
- The UI runtime still has Flyway switched off; the Management server (or the Update Server) owns the schema.

Verified: the new Management server creates the schema on a fresh MySQL 8.0 database (every test copy and the release image check), and the test suite runs on H2.

**Verified on a test copy (29 October 2026):** the new Management server and UI started on a MySQL 8.0 database, message broker and artifact store that the original version had been using (with targets, distribution sets, software modules, uploaded artifacts, target filters, a rollout and actions created through the original UI and API). Flyway 12.4 validated the 47 existing migrations and reported the schema up to date: no migration ran, `schema_version` was unchanged (47 rows, none failed), there were no errors in either log, the record counts were identical before and after, and every view showed the original data (artifact checksums included). Not covered: the new version writing to that data over a longer period, and databases other than MySQL 8.0.

Before production, repeat the rehearsal on a copy of your own data:

1. Back up the production database and restore it to a test server.
2. Start the new Management server (or Update Server) against the copy.
3. Check that the log shows no pending migration and no validation error, and that `schema_version` is unchanged.
4. Sign in to the UI and open each view.

## Expected log lines at startup

The migrated programs log some WARN lines at startup that the original did not. They are expected and harmless; do not raise alerts on them. Two programs were compared with the original: the UI runtime and the Management server (28 October 2026), and their startup logs were counted again on the final code (30 October 2026), with the same lines and counts. In all, the UI runtime logs 22 WARN lines at startup and the Management server 20; 5 and 1 of them are also in the original's log (below the table).

| Logger (short) | Message starts with | Where, how many | Why |
|---|---|---|---|
| `PostProcessorRegistrationDelegate$BeanPostProcessorChecker` | "Bean '…' of type […] is not eligible for getting processed by all BeanPostProcessors" | 16 in the UI runtime and 16 in the Management server: the same 15 beans in both (data source, JPA and Spring Cloud beans and hawkBit's `RepositoryApplicationConfiguration`), plus one in the UI runtime for the event bus (`EventBusConfiguration`) and one in the Management server for Flyway's version converter (`stringOrNumberMigrationVersionConverter`) | hawkBit declares two post-processor beans as instance methods, as before; Spring 7 now logs this at WARN (Spring 5 logged it at INFO). Kept unchanged for parity |
| `InitializeUserDetailsManagerConfigurer` | "Global AuthenticationManager configured with an AuthenticationProvider bean. UserDetailsService beans will not be used …" | 1 in the UI runtime, 1 in the Management server | Spring Security 7 message. Sign-in works as before |
| `SpringDocAppInitializer` | "SpringDoc /swagger-ui.html endpoint is enabled by default …" and "SpringDoc /v3/api-docs endpoint is enabled by default …" | 2 in the Management server | springdoc 3 message. The original serves the same endpoints without saying so. To switch them off in production: `springdoc.swagger-ui.enabled=false`, `springdoc.api-docs.enabled=false` |
| `HV000272` (Hibernate Validator) | A message about `@Valid` on a container | 1 on the first validated use of two methods after each start (not at startup itself) | Section 2.4 |

Not new, also in the original's log: four "You are asking Spring Security to ignore …" lines in the UI runtime (now naming `PathPattern` instead of `Ant`) and "spring.jpa.open-in-view is enabled by default" in both programs. The startup logs of the Update Server, DDI server and DMF server were not compared with the original.

## Content-Security-Policy

hawkBit sets no Content-Security-Policy by default, as before. If you set one, through `hawkbit.server.security.content-security-policy` or in a proxy:

- **`script-src` must allow `'unsafe-eval'` for the UI.** Without it the UI does not start: the login page stays blank. The original version behaves the same.
- Three hawkBit UI features run a script that the server sends (Vaadin's `executeJs`), and these also need `'unsafe-eval'`: the forward of old `#!` bookmarks, setting the focus when a page or window opens, and the colours of software module types in the Distributions view. No other hawkBit code uses `executeJs`.
- The policy below (one header value, shown one directive per line) was tested during the migration on both versions: the UI started and forwarded old `#!` addresses, with no console errors. It was tested on the UI runtime in an earlier stage of the migration and not repeated on the final code; test your own policy on a test copy.

```text
default-src 'self';
script-src 'self' 'unsafe-inline' 'unsafe-eval';
style-src 'self' 'unsafe-inline';
img-src 'self' data:;
font-src 'self' data:;
connect-src 'self'
```

## Security before production

Read this with section 7.1 (pre-existing issues) before you put the migrated version into production.

1. **Human security review of anonymous Vaadin requests to the login addresses.** The original answered 404 (or showed the login page) to Vaadin's protocol requests (`v-r=uidl`, `heartbeat`, `push`, `i18n`) sent without a session to `/UI/login`, `/UI/login/` and `/UI/login/...`. The migrated app gives Vaadin's standard answers for a request without a session (200 with a "session expired" JSON, 403 or 404) and sets no cookie. Browsers never send these requests; only crafted ones do. You accepted this (28 and 29 October 2026) and asked for a review by a security reviewer before production. **Owner: your security reviewer.** Check whether any of these answers creates state on the server or can be used for load (denial of service), and whether your proxy should block them. A plain load of the login page creates a session in both versions.
2. **Two documented exceptions from one Vaadin safeguard (accepted).** Vaadin ignores requests from parts of a page that sit behind an open modal window. The artifact upload drop area is allowed to receive its own uploads, and its drop listener its own drop events, while its modal "Upload status" window is open. The listener carries only file names and sizes and decides nothing on the server; the upload still needs a signed-in user with upload permission, and every server-side check applies. You accepted both (29 and 30 October 2026). Re-check them after each Vaadin upgrade (section 4.2).
3. **Content-Security-Policy** needs `'unsafe-eval'` (section 5.8).
4. **No vulnerability scanning.** The upstream project's nightly scan was removed with its workflows, together with its two recorded CVE acceptances (`.trivyignore`, section 2.5). Nothing scans the new dependency set. Add a dependency scan to CI and review its result before release.
5. **Pre-existing issues kept for parity** (section 7.1). For production, the most important are:
   - the open redirect and cross-site sign-out: `/UI/logout?login=<any address>` signs the user out and sends them to that address, and a link from another site can sign a user out;
   - the leftover device login on a message thread: after a device download request over DMF, the message listener's thread keeps the last device's login and tenant until the next message replaces them. Nothing reads it today, but code added later that reads the login before authenticating would see another tenant. The original behaves the same;
   - the shipped default passwords.

## Third-party dependency list

`.3rd-party/DEPENDENCIES` is the licence inventory of all third-party libraries. It still lists the original stack (Vaadin 8, Spring 5.3, …), apart from the edited line for the copied event bus. It is generated with the Eclipse Dash licence tool, which asks Eclipse Foundation services about each library. The migration did not run it, because the migration agreed to make no contact with the upstream Eclipse project and its services. **Regenerate the list before you distribute the software.**

From the repository root, with JDK 21, Maven 3.9 and network access to `repo.eclipse.org` (the tool's Maven plugin) and the Eclipse Foundation's licence service:

```bash
./check-dependencies.sh
```

The script runs:

```bash
mvn clean install -DskipTests -Ddash.skip=false \
  --projects '!org.eclipse.hawkbit:hawkbit-repository-test,!org.eclipse.hawkbit:hawkbit-dmf-rabbitmq-test' \
  -Ddash.summary=.3rd-party/DEPENDENCIES
```

- `-Ddash.skip=false` switches on the `eclipse-dash` profile, which adds the tool's plugin repository.
- With an Eclipse IP Lab access token as the first argument (`./check-dependencies.sh <token>`), the tool also files review requests for libraries it cannot vet, listed in `.3rd-party/DEPENDENCIES_REVIEW`. Never commit or share the token.
- The build fails if a library is not approved (`dash.fail=true`). Many versions are new with this migration and may need a review first.
- The Vaadin 24 simple UI is not in the default build, so its libraries are no longer listed.
- The event bus is copied source, not a Maven dependency, so the tool will not list it. Add its line back by hand after regenerating (Apache-2.0; licence text in `licenses/APACHE-2.0.txt`).

## Cut-over checklist

1. Install Java 21 on the hosts, or use the new images.
2. Rename the configuration properties of section 2.2 in your own configuration.
3. Replace launcher class names in your start scripts (`org.springframework.boot.loader.launch.*`).
4. Rebuild images from this source. Never pull `hawkbit/*` from Docker Hub for this version. For the MySQL flavour, re-check the driver download stage first (section 5.5).
5. Update proxies, monitors and scanners for the address and status changes in section 2.3: `/UI/<view>` addresses, 404 for unknown or forbidden UI views, 400 for rejected requests, `X-XSS-Protection: 0`.
6. Tell log monitoring about the expected startup WARN lines (section 5.7).
7. If you set a Content-Security-Policy, allow `'unsafe-eval'` (section 5.8).
8. If you use a custom Sass theme, redo it as a CSS file; move a custom login footer to its new path (`docs/content/guides/customtheme.md`).
9. If you integrate over DMF with your own message types, or read Spring Cloud Bus headers, see section 2.4.
10. Rehearse the database start on a copy (section 5.6).
11. Have the security review done and run a dependency scan (section 5.9), and change all default passwords (section 7.1).
12. Regenerate the third-party dependency list before distribution (section 5.10).

# How to test

## Unit and integration tests

```bash
mvn verify
```

The 89 DMF integration tests need RabbitMQ. Without a reachable broker they are **skipped silently** (pre-existing). Set `RABBITMQ_TEST_HOSTNAME=<broker host>` and `RABBITMQ_SERVER_REQUIRED=true` so that they run, or fail if the broker is missing. Some test classes depend on the order they run in (pre-existing). Run them in alphabetical order as CI does: `-Dsurefire.runOrder=alphabetical`.

Last results, on commit `684b25a`: the full backend and UI suite ran 1699 tests with 0 failures, 0 errors and 0 skipped, and the CI subset (the UI runtime and its modules, offline, with RabbitMQ) ran 1672 tests with 0 failures. Later commits change only documentation and code comments.

## Browser end-to-end tests

```bash
APP_ENV_PROJECT=copy-a APP_ENV_PORT=18181 \
  APP_ENV_USERS_PROPERTIES=<users file> APP_ENV_USERS_ENV=<password file> env/e2e.sh
```

`env/e2e.sh` starts a fresh copy, runs `mvn -Pe2e -pl hawkbit-runtime/hawkbit-vv8-ui-e2e verify` against it and removes it.
- **Needs:** JDK 21 and Maven 3.9 on the host. The first run downloads Chromium headless shell from `cdn.playwright.dev`.
- **Time:** about 6 minutes locally.
- **Users:** the test user `admin`, with its password in the password file as `MIG_TU_ADMIN_PW`. `.github/ci/test-users.sh` writes throw-away users with random passwords.
- **Never commit password files.**
- **On failure:** screenshots go to `hawkbit-runtime/hawkbit-vv8-ui-e2e/target/e2e/`.
- **More:** `env/README.md`, "End-to-end tests".

## Continuous integration

GitHub Actions workflow `.github/workflows/ci.yml` (`.github/CI.md` describes it in full):

- **When it runs:** on push to `main`, `mig-dev`, `stage/**`, `stabilise/**`, `fix/**`, and by hand. There is no `pull_request` trigger, so a commit is built once: a commit that already passed is skipped unless the run is started with `force`.
- **Job `build-test-smoke`:**
  - builds the image and starts a copy;
  - logs in four throw-away users and opens their views by address and from the menu (36 checks, other hosts blocked);
  - runs the test suite offline with RabbitMQ.
  - Takes about 15-16 minutes with a warm cache.
- **Job `e2e`:** builds its own copy and runs the six browser tests, in about 9 minutes, in parallel with `build-test-smoke`.
- **On failure:** diagnostics are uploaded for 3 days, with test-user passwords removed. No repository secret is needed.

## Manual check

Start a copy with `env/up.sh`. Then sign in as an administrator and as a read-only user, open each view, and follow one workflow from each area: target, distribution set, upload, rollout, system configuration. The side-by-side walkthrough delivered with these docs (section 8) shows the expected screens.

# Known pre-existing issues

These issues exist in the original application. They were found during the migration and **kept as they were**, because the migration's rule is parity first: changing them was not part of the agreed work. The first three security items were confirmed as "keep and log" in the planning decisions. Where the migrated app behaves differently, the table says so.

## Security

| Issue | Where | Note |
|---|---|---|
| **Shipped default passwords**: UI administrator with a well-known password stored in plain text and all permissions; RabbitMQ `guest` account; MySQL profile with an empty root password | `application.properties` of the runtimes, `hawkbit-security-defaults.properties` | Documented upstream default. **Override them in every deployment** |
| **Open redirect and cross-site sign-out:** `/UI/logout?login=<any URL>` signs the user out and sends them to that site, and sign-out accepts a plain link from another site | Logout success handler, `/UI/**` without CSRF | Kept. A good candidate for follow-up work |
| **Login throttling can be bypassed:** the only throttle keys on the client-supplied `X-Forwarded-For` header and skips private ranges. There is no account lockout | `DosFilter`, `IpUtil` | Kept |
| **Leftover device login on a DMF message thread:** after a download authentication message, the listener keeps the last device's login and tenant on its pooled thread. The next message authenticates first, so nothing reads it today | `hawkbit-dmf-amqp`, `AmqpAuthenticationMessageHandler` | Same in both versions. Found by a test written during the migration; code that reads the login before authenticating would see another tenant |
| No new session id at login (session fixation) | UI login | Same in both apps; an attacker would need to plant a session id |
| Some static paths bypass the security filter chain: `/documentation/**`, `/docs/**`, `/VAADIN/**` and any root-level address with a dot (`/*.*`) skip the whole chain, including the security headers | UI security configuration (ignored paths) | Same four paths in both versions |
| A software module type colour set through the API is put into the page unescaped | Distributions view | The migrated app limits it to a CSS rule (old: script) |
| The bulk upload failure reason is shown as HTML (self-reflected only) | Bulk upload window | Kept |
| OIDC logout handler races between concurrent sign-outs | OIDC configuration | Not on the verified path (OIDC not verified) |

## Reliability and data

| Issue | Note |
|---|---|
| Each target-count footer in Deployment and Target Filters starts a thread that is never stopped, and that keeps the last user's security context | Still present in the migrated code. The soak test (400 sign-ins per app) saw no thread growth on either app |
| Live updates: three busy sessions can stall push updates for all users and the shared scheduled jobs; more than 10,000 queued events are dropped silently | Under concurrent load |
| One rollout that fails during handling stops rollout handling for the whole tenant: other rollouts stay in "Creating" | Seen when a rollout's creator is not a known user on the Management server |
| A database or schema problem at login is reported as "authentication failed", logged at DEBUG only | |
| Bulk upload reads the CSV in the platform's default charset. On Java 21 this is UTF-8; on the original Java 17 it followed the host locale | Not compared; non-ASCII names may be read differently |
| Bulk upload: an error on one line stops the rest of the file; blank and duplicate lines count as failed; only lowercase `.csv` is accepted. The footer count stays at the old total until the view is reopened | |
| Artifact upload can block for good when the upload thread pool is full (heavy concurrent uploads only) | |
| System Config save writes each group separately, with no transaction. An invalid polling time plus another change reports "Saved changes", but the invalid value is not stored | |
| Two users editing the same item: the last save wins silently | |
| Recognised UI errors are shown but not logged. Some validation failures show raw technical messages | |
| Grid search treats `%` and `_` as wildcards. Sorting by a column with equal values can repeat or skip rows while scrolling | |
| An open browser tab keeps its session alive indefinitely (heartbeat on, idle sessions not closed) | |
| Times can be one hour off in summer: the browser time zone is guessed from the UTC offset only. Set `hawkbit.server.ui.fixed-time-zone` to avoid it | Kept as agreed |
| A link opened before login is lost after login | Kept |
| In the rollout "Advanced group" definition, removing a row after an earlier row was removed may remove the wrong row | Inferred from code |
| `spring.main.allow-bean-definition-overriding=true` hides duplicate bean definitions | |
| Malformed addresses such as `/UI;jsessionid=x/` and a Management API body `[null]` answer 500 with a stack trace | Same status in both apps |
| The five status filters in Deployment have no accessible name (a screen reader says "button") | Kept |
| The target status after an auto-assignment stays "unknown" until the view is reopened | **Better in the migrated app**: updates live |
| Opening "Create new Rollout" logged 4 browser console errors from the old pie chart | The chart is redrawn; the errors are expected to be gone (not separately confirmed) |

# Side-by-side recordings

Every one of the 31 workflows was recorded step by step on the original and on the migrated application, with the same data, test users, browser and window size. **The side-by-side walkthrough delivered with these docs** is one self-contained folder (also delivered as a zip) with a page per workflow. Each page shows the workflow's steps, with the original's screenshot on the left and the migrated app's on the right. Steps with an accepted difference say what differs and that it was accepted.

**How to open it:** unzip the folder anywhere and open `index.html` in a current browser. It works offline from the file system; no server, login or network is needed. The pages hold no passwords or security tokens.

The workflows, numbered as in the walkthrough:

| # | Workflow | # | Workflow |
|---|---|---|---|
| 1 | Log in, look around and sign out | 17 | Edit and delete distribution sets, and the module rules |
| 2 | Log in with a wrong password | 18 | Create a software module type and a distribution set type |
| 3 | Stay signed in while idle in an open tab | 19 | Create a software module and upload artifacts |
| 4 | Create, edit and delete a target | 20 | Upload, download and delete an artifact (repository operator) |
| 5 | Search and filter the target list, keep the filter over a reload | 21 | Create, use, edit and delete a target filter |
| 6 | Create a target tag, tag targets, filter and remove the tag | 22 | Auto-assign a distribution set through a target filter |
| 7 | Assign a distribution set to a target by drag and drop | 23 | Create a rollout and start it (approval off) |
| 8 | Assign several targets to a distribution set at once | 24 | Pause, resume, trigger the next group and delete a rollout |
| 9 | Assign a distribution set to all targets with a tag | 25 | Define rollout groups by hand, copy and edit a rollout |
| 10 | Cancel an update action and force-quit it | 26 | Rollout with approval switched on |
| 11 | Create targets in bulk from a CSV file | 27 | Change, keep and undo system configuration |
| 12 | Tag distribution sets and filter by tag | 28 | What the read-only user can and cannot do |
| 13 | Invalidate a distribution set | 29 | What the target operator can and cannot do |
| 14 | See another session's changes without reloading | 30 | What the repository operator can and cannot do |
| 15 | A device installs an assigned update | 31 | A target created through the REST API appears without a reload |
| 16 | Create a distribution set with modules and metadata | | |

Verdicts on the code this document describes:
- 25 pass.
- 3 are accepted because forbidden views now answer 404: workflows 28, 29 and 30 (accepted 28 October 2026).
- 3 are accepted because field error messages now show under the field: workflows 4, 21 and 27 (accepted 29 October 2026).

Six of the workflows also run as browser tests in the repository (section 6.2): 1, 2, 6, 17, 19 and 24.

# Follow-up candidates (parking lot)

During the migration, improvements that were not needed for parity were written down instead of built. They are input for a possible follow-up. Only the items of section 9.1 are needed before production; none blocks the hand-off. Size: tiny = under an hour, small = up to a day.

## Before production

- **Security review** of anonymous Vaadin requests to the login addresses, together with the other security items of section 7.1 (section 5.9). Owner: your security reviewer. Small (review); the size of any fix is unknown.
- **Dependency scan** of the new stack, and a scan step in CI (section 5.9). Small.

## Security and accessibility

- Close the open redirect at sign-out and add CSRF protection to sign-out; anchor the login throttle and add an account lockout; change the session id at login (pre-existing issues above).
- Give every icon-only button an accessible name that follows its tooltip (small). The three drop-downs of the System Config duration pop-up have no accessible names, as in the original (tiny).
- Menu text contrast is about 3.5:1 (WCAG AA needs 4.5:1), and yellow on white is used for pending status. White text on module type colours is about 3.8:1. All of these are the original colours (tiny each).
- Disabled buttons outside grids show no tooltip; Vaadin 8 showed one (small).
- Upload hardening against crafted requests, not reachable from a normal browser (small each): no cap on the number of files a crafted drop event lists; a file over the size limit with an illegal name gets the size reason instead of the name reason; whether a disabled drop zone still delivers drop events is not confirmed; a file just over the limit that bypasses the browser check stays "in progress" in the upload button's list.
- Pin the CI's JDK download by checksum (small).

## Operations and build

- Release images: raise the MariaDB driver default to 3.5.10 and let `build_all_dev.sh` pass it; re-run the mysql driver stage once the download host answers (tiny).
- The upstream CircleCI and Azure files still name JDK 17, and the dev container uses Java 8. Update or remove them (small).
- `/UI;jsessionid=x/` and a `[null]` request body answer 500; both could answer 400 (small).
- The refused-upload drain limits (100 MB / 60 s) are constants. A probe saw one unreproduced 500 right after a refused 200 MB upload (small).
- The favicon is served only at the host root, so a deployment under a path prefix shows no tab icon (small).
- The shell banner still says "This Vaadin 8 hawkBit UI … is deprecated". The text is yours to decide (tiny).
- Log noise from framework races, no user effect: a rare Atmosphere ERROR line when a push connection closes; Vaadin's session cleanup can throw an `IllegalStateException` when many heartbeats overlap a sign-out (seen only under a deliberate flood); hawkBit's UI error handler lacks one null check for other errors on a heartbeat (tiny to small).
- No `.gitignore` for the frontend build's generated folder in the two UI runtimes (small).
- Dead setting `spring.mvc.favicon.enabled` (tiny).
- `env/`: the helper containers use Python 3.9.6, which is past end of life (small); `reset.sh` occasionally takes about 60 s (small).
- Comments in shipped files still carry migration references (small).

## Look (restoration toward the original pixels)

- Ship Open Sans (light weight) for the exact original typeface: a font licence and asset decision (small).
- Striped grid rows and a shaded header, the dropdown chevron in the user menu, and the drop-area highlight (small each).
- Smaller details (tiny each):
  - links underlined in blue;
  - Valo's corner radius and white fields;
  - disabled primary buttons at half opacity;
  - the legend spacing in the rollout window;
  - the progress bar height;
  - bold names in window titles;
  - the detail-tab marker;
  - the menu spacing;
  - the "Actions" caption covered at 1400 px;
  - incomplete sets not greyed;
  - the bulk-upload tag row;
  - the order of the rollout status bar segments;
  - labels meant to be small or bold look normal (two CSS classes without a rule).
- The date pickers show a four-digit year, and the time is cut in the rollout window at 1400 px (small).
- In a 1024 px window the Rollout view is wider than the window: the Rollouts grid and its header run past the right edge. The original behaves the same (small).
- The open search field in grid headers has no minimum width: below a 1024 px window it can become too narrow to read the typed text (tiny).

## Code and tests

- Small tidy-ups:
  - 51 class names and identifiers still named after the Vaadin 8 Valo theme (`valo-*`);
  - two copies of colour parsing;
  - casts left from automated edits;
  - a null id passed to `setId` in two builders;
  - the upload button using the thread's current UI;
  - `ExpandRatioLayout` not normalising ratios that add up to less than 1.
- Grid cells with tooltips are built twice per refresh (CPU only; the soak test showed no growth) (small).
- The drop area's client file list is never emptied in a long-lived tab (small).
- Upload status rows in arrival order rather than drop order (small, only if asked).
- The drop area's own drop listener works around a Vaadin limitation; remove it if a later Vaadin lets the upload manager's reject event run behind a modal window (small).
- DMF integration tests use a converter that trusts all packages, unlike production (small).
- Dependency hygiene in `hawkbit-dmf-amqp` (small).
- Some RSQL tests may use random 100-character names with prefix filters that collide about once in 480 runs; check their test data (small).
- End-to-end tests:
  - rename `Wf01LoginLogoutIT` by feature (tiny);
  - check the data the UI does not show, for example tag membership and stored files, through a small Management API client (medium).

# Appendix A. Before this document is final {-}

This release candidate becomes the final version when you accept the hand-off. What remains is your test run and acceptance of the hand-off (M3), on the M3 branch `stage/3-handoff` (the M3 pull request); after your acceptance it merges into `mig-dev`. The workflow milestone (M2) passed on 30 October 2026, and the final workflow runs on the hand-off code are done and reported in the final migration report.

After acceptance, your own steps before production are in sections 5.5 (MySQL image driver check), 5.6, 5.9 and 5.10 and in the cut-over checklist (section 5.11).
