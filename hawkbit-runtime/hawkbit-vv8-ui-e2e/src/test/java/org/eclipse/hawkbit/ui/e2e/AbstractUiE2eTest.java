/**
 * Copyright (c) 2023 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.hawkbit.ui.e2e;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.extension.AfterTestExecutionCallback;
import org.junit.jupiter.api.extension.RegisterExtension;

/**
 * Base of the UI end-to-end tests: one headless Chromium per test class, a fresh browser context (own session) per
 * test, the app returned to its seed state before each class, a screenshot into {@code target/e2e/} when a test fails.
 * <p>
 * Settings (system properties, set by the {@code e2e} profile from Maven properties of the same name):
 * {@code e2e.baseUrl} (the running UI), {@code e2e.usersEnv} (the test users' password file; else the environment
 * variable {@code APP_ENV_USERS_ENV}), {@code e2e.reset} (command that resets the app before each class; empty: none),
 * {@code e2e.timezone} (browser time zone), {@code e2e.outputDir} (screenshots).
 * <p>
 * The browser may reach only the app's host and port: every other request is aborted and fails the test, as a view
 * that leaves the app would. Console errors, page errors and HTTP responses of 400 or more fail the step they happen in.
 */
abstract class AbstractUiE2eTest {

    /** Base URL of the UI, without trailing slash. */
    protected static final String BASE_URL = System.getProperty("e2e.baseUrl", "http://127.0.0.1:18081").replaceAll("/+$", "");
    private static final String TIMEZONE = System.getProperty("e2e.timezone", "Europe/Helsinki");
    private static final Path OUTPUT_DIR = Paths.get(System.getProperty("e2e.outputDir", "target/e2e"));

    private static Playwright playwright;
    private static Browser browser;
    private static String userAgent;

    private BrowserContext context;
    /** The test's page. */
    protected Page page;
    /** The app as a user sees it, on {@link #page}. */
    protected HawkbitUi ui;
    private final List<String> browserErrors = Collections.synchronizedList(new ArrayList<>());
    private final List<String> blockedRequests = Collections.synchronizedList(new ArrayList<>());
    private String testName;

    @BeforeAll
    static void startBrowser() throws IOException, InterruptedException {
        resetApp();
        // Chromium only: the pom installs its headless shell; Playwright must not fetch its other browsers.
        playwright = Playwright.create(new Playwright.CreateOptions().setEnv(Map.of("PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD", "1")));
        browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
        // The user agent of the desktop browser, as the migration's workflow runs use (no "HeadlessChrome" token)
        try (BrowserContext probe = browser.newContext()) {
            userAgent = ((String) probe.newPage().evaluate("() => navigator.userAgent")).replace("HeadlessChrome", "Chrome");
        }
    }

    @AfterAll
    static void stopBrowser() {
        if (browser != null) {
            browser.close();
        }
        if (playwright != null) {
            playwright.close();
        }
    }

    @BeforeEach
    void openContext(final TestInfo info) {
        testName = info.getTestClass().map(Class::getSimpleName).orElse("test") + "-" + info.getTestMethod().map(m -> m.getName()).orElse("");
        final URI base = URI.create(BASE_URL);
        final String appOrigin = base.getScheme() + "://" + base.getAuthority();
        context = browser.newContext(new Browser.NewContextOptions().setViewportSize(1400, 900).setTimezoneId(TIMEZONE)
                .setUserAgent(userAgent));
        // Only the app's own host and port; data:, blob: and about: URLs never reach the network.
        context.route(url -> url.matches("^(https?|wss?)://.*") && !url.startsWith(appOrigin + "/") && !url.equals(appOrigin),
                route -> {
                    blockedRequests.add(route.request().url());
                    route.abort("blockedbyclient");
                });
        // A target's security token is a secret: blank it on every page, so no screenshot shows it.
        context.addInitScript("(() => { const add = () => { const s = document.createElement('style');"
                + " s.textContent = '[id=\"target.security.token\"] { visibility: hidden !important; }';"
                + " (document.head || document.documentElement).appendChild(s); };"
                + " if (document.head) add(); else document.addEventListener('DOMContentLoaded', add); })()");
        page = context.newPage();
        page.onConsoleMessage(m -> {
            if ("error".equals(m.type()) && !m.text().contains("ERR_BLOCKED_BY_CLIENT")) {
                browserErrors.add("console: " + truncate(m.text()));
            }
        });
        page.onPageError(e -> browserErrors.add("page error: " + truncate(e)));
        page.onResponse(r -> {
            if (r.status() >= 400) {
                browserErrors.add("HTTP " + r.status() + " " + truncate(r.url()));
            }
        });
        ui = new HawkbitUi(page, BASE_URL);
    }

    @AfterEach
    void closeContext() {
        if (context != null) {
            context.close();
        }
    }

    /** Takes a full-page screenshot of the test's page when the test failed, before {@link #closeContext()}. */
    @RegisterExtension
    final AfterTestExecutionCallback screenshotOnFailure = ext -> {
        if (ext.getExecutionException().isPresent() && page != null && !page.isClosed()) {
            Files.createDirectories(OUTPUT_DIR);
            final Path shot = OUTPUT_DIR.resolve(testName + "-failure.png");
            page.screenshot(new Page.ScreenshotOptions().setPath(shot).setFullPage(true));
            System.err.println("Screenshot of the failure: " + shot.toAbsolutePath());
        }
    };

    /** A check of one step: performs it; nothing else. */
    @FunctionalInterface
    protected interface StepBody {
        void run() throws Exception;
    }

    /**
     * One workflow step: runs {@code body} (actions and assertions), then fails the step on browser errors or requests
     * that left the app. Failures name the step.
     */
    protected void step(final int number, final String description, final StepBody body) {
        browserErrors.clear();
        try {
            body.run();
        } catch (final AssertionError | Exception e) {
            // No password in reports or logs: Playwright's call log of a failed fill() can hold the typed value. The
            // message is cleared of every test-user password, and the original exception (whose message and causes
            // would keep it) is not attached; its stack trace is.
            final AssertionError failure = new AssertionError(
                    HawkbitUi.TestUsers.redact("Step " + number + " (" + description + "): " + e.getClass().getSimpleName() + ": " + e.getMessage()));
            failure.setStackTrace(e.getStackTrace());
            throw failure;
        }
        page.waitForTimeout(400);
        assertEquals(List.of(), List.copyOf(blockedRequests), "Step " + number + " (" + description + "): requests to other hosts");
        assertEquals(List.of(), List.copyOf(browserErrors), "Step " + number + " (" + description + "): browser errors");
    }

    /** Runs the {@code e2e.reset} command, if set, and waits for it (at most 5 minutes). */
    private static void resetApp() throws IOException, InterruptedException {
        final String cmd = System.getProperty("e2e.reset", "").trim();
        if (cmd.isEmpty()) {
            return;
        }
        // its output goes to a file: the forked test JVM must not write to its own stdout directly (failsafe's channel)
        Files.createDirectories(OUTPUT_DIR);
        final Path log = OUTPUT_DIR.resolve("reset.log");
        final Process p = new ProcessBuilder(cmd).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.appendTo(log.toFile()))
                .start();
        if (!p.waitFor(5, TimeUnit.MINUTES)) {
            p.destroyForcibly();
            fail("Reset of the app (" + cmd + ") did not finish within 5 minutes (log " + log.toAbsolutePath() + ")");
        }
        if (p.exitValue() != 0) {
            fail("Reset of the app (" + cmd + ") failed with exit code " + p.exitValue() + " (log " + log.toAbsolutePath() + ")");
        }
    }

    private static String truncate(final String s) {
        return s.length() > 300 ? s.substring(0, 300) : s;
    }
}
