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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.LoadState;

/**
 * The hawkBit UI as a user sees it: login, main menu, views, user menu, tooltips, notifications. Locators are
 * user-level (roles, labels, visible text). Where Flow components keep their text in light DOM slots (menu items,
 * tooltips, notification cards), the text is read through the slots. Only the public tags and attributes of Vaadin
 * components are used ({@code vaadin-tooltip}, {@code vaadin-tooltip-overlay[opened]}, {@code vaadin-notification-card}).
 */
final class HawkbitUi {

    /** The main menu of a user with every permission, in menu order. */
    static final List<String> VIEWS = List.of("Deployment", "Rollout", "Target Filters", "Distributions", "Upload", "System Config");

    private static final Map<String, String> ROUTES = Map.of("login", "/UI/login", "start", "/UI/", "Deployment", "/UI/deployment",
            "Rollout", "/UI/rollout", "Target Filters", "/UI/targetFilters", "Distributions", "/UI/distributions", "Upload",
            "/UI/spUpload", "System Config", "/UI/spSystemConfig");

    /** The heading of each view, and the texts that show its content is in place. */
    private static final Map<String, String> VIEW_TITLE = new LinkedHashMap<>();
    private static final Map<String, List<String>> VIEW_CONTENT = Map.of("Deployment", List.of("Targets", "Distributions", "Action history"),
            "Rollout", List.of("Rollouts"), "Target Filters", List.of("Custom Filter"), "Distributions", List.of("Distributions", "Software Module"),
            "Upload", List.of("Software Module", "Drop Files to upload"), "System Config",
            List.of("Distribution Configuration", "Repository Configuration"));

    static {
        VIEW_TITLE.put("Deployment", "Deployment Management");
        VIEW_TITLE.put("Rollout", "Rollout Management");
        VIEW_TITLE.put("Target Filters", "Target Filter Management");
        VIEW_TITLE.put("Distributions", "Distributions Management");
        VIEW_TITLE.put("Upload", "Upload Management");
        VIEW_TITLE.put("System Config", "System Configuration");
    }

    /**
     * Visible text of an element, read through slots and shadow roots (innerText leaves out what is slotted into a
     * shadow tree). Tooltip elements are left out: their text is not on screen.
     */
    private static final String FLAT_TEXT = "(el) => { const ft = (n) => {"
            + " if (n.nodeType === 3) return n.textContent; if (n.nodeType !== 1) return '';"
            + " if (n.hidden || getComputedStyle(n).display === 'none' || n.tagName === 'VAADIN-TOOLTIP') return '';"
            + " if (n.tagName === 'SLOT') { const a = n.assignedNodes({ flatten: true }); return (a.length ? a : [...n.childNodes]).map(ft).join(' '); }"
            + " return (n.shadowRoot ? [...n.shadowRoot.childNodes] : [...n.childNodes]).map(ft).join(' '); };"
            + " return ft(el).replace(/\\s+/g, ' ').trim(); }";

    /**
     * Marks the element a tooltip belongs to: {@code el} itself or the first element inside it (slotted content
     * included) that a {@code vaadin-tooltip} is attached to. Returns whether one inside was marked.
     */
    private static final String MARK_TOOLTIP_TARGET = "(el, tag) => {"
            + " const tips = [...document.querySelectorAll('vaadin-tooltip')].map(t => t.target).filter(Boolean);"
            + " if (tips.includes(el)) return false; const roots = [el];"
            + " for (const slot of el.querySelectorAll('slot')) roots.push(...slot.assignedElements({ flatten: true }));"
            + " for (const r of roots) for (const e of [r, ...r.querySelectorAll('*')]) if (tips.includes(e)) { e.setAttribute('data-e2e-tip', tag); return true; }"
            + " return false; }";

    /** True when no Flow client has a server round trip in flight (the check TestBench waits for). */
    private static final String FLOW_IDLE = "() => { const c = window.Vaadin && window.Vaadin.Flow && window.Vaadin.Flow.clients;"
            + " return !c || !Object.values(c).some(x => x && x.isActive && x.isActive()); }";

    private final Page page;
    private final String baseUrl;
    private int tipSeq;

    HawkbitUi(final Page page, final String baseUrl) {
        this.page = page;
        this.baseUrl = baseUrl;
    }

    // ---- waiting

    /** Waits until the page is quiet: loaded, then (after {@code ms}) no server round trip in flight. */
    void settle(final int ms) {
        try {
            page.waitForLoadState(LoadState.LOAD);
        } catch (final PlaywrightException e) {
            // a navigation in progress: the wait below covers it
        }
        page.waitForTimeout(ms);
        try {
            page.waitForFunction(FLOW_IDLE, null, new Page.WaitForFunctionOptions().setTimeout(10_000).setPollingInterval(100));
        } catch (final PlaywrightException e) {
            // not a Flow page (or still busy): callers check the outcome
        }
    }

    void settle() {
        settle(800);
    }

    /** Polls {@code condition} every 250 ms until it holds or {@code ms} have passed; returns its last value. */
    boolean waitFor(final BooleanSupplier condition, final int ms) {
        final long end = System.currentTimeMillis() + ms;
        while (true) {
            boolean ok;
            try {
                ok = condition.getAsBoolean();
            } catch (final PlaywrightException e) {
                ok = false;
            }
            if (ok || System.currentTimeMillis() >= end) {
                return ok;
            }
            page.waitForTimeout(250);
        }
    }

    /** The page's visible text, white space collapsed. */
    String pageText() {
        return page.locator("body").innerText().replaceAll("\\s+", " ");
    }

    boolean waitText(final String text, final int ms) {
        return waitFor(() -> pageText().contains(text), ms);
    }

    /** Visible text of the element, through slots and shadow roots, icon glyphs dropped. */
    static String textOf(final Locator loc) {
        return clean((String) loc.evaluate(FLAT_TEXT));
    }

    /** White space collapsed, icon-font glyphs (Private Use Area characters) dropped. */
    static String clean(final String s) {
        return s == null ? "" : s.replaceAll("[\\uE000-\\uF8FF]", " ").replaceAll("\\s+", " ").trim();
    }

    // ---- addresses and login

    /** Opens an app address by typing it in the browser: a view name, "start" or "login", or a path. */
    void openAddress(final String where) {
        page.navigate(baseUrl + ROUTES.getOrDefault(where, where));
        settle(1500);
    }

    private Locator signInButton() {
        return page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Sign in"));
    }

    private Locator passwordField() {
        // exact: the reveal button's label "Show password" also matches "Password"
        return page.getByLabel("Password", new Page.GetByLabelOptions().setExact(true));
    }

    /** The login page: a username field, a password field and a sign-in button. */
    boolean isLoginPage() {
        return page.getByLabel("Username").count() > 0 && passwordField().count() > 0 && signInButton().isVisible();
    }

    /** Waits up to {@code ms} until the login page is in place (after a redirect). */
    boolean waitLoginPage(final int ms) {
        try {
            signInButton().first().waitFor(new Locator.WaitForOptions().setTimeout(ms));
        } catch (final PlaywrightException e) {
            // checked below
        }
        settle();
        return isLoginPage();
    }

    boolean hasTenantField() {
        return page.getByLabel("Tenant").count() > 0;
    }

    /** Signs in from the login page and waits until the main menu is there. */
    void login(final String user) {
        page.getByLabel("Username").fill(user);
        passwordField().fill(TestUsers.password(user));
        signInButton().click();
        try {
            menuItem("Deployment").waitFor(new Locator.WaitForOptions().setTimeout(30_000));
        } catch (final PlaywrightException e) {
            // the caller checks the view
        }
        settle(1500);
    }

    // ---- main menu and views

    /** A main menu entry by its visible text (a button whose name may start with an icon glyph). */
    Locator menuItem(final String name) {
        return page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("^\\W*" + Pattern.quote(name) + "\\s*$")))
                .first();
    }

    boolean menuItemVisible(final String name) {
        try {
            return menuItem(name).isVisible();
        } catch (final PlaywrightException e) {
            return false;
        }
    }

    /** The main menu entries a user sees, in menu order. */
    List<String> menuEntries() {
        return VIEWS.stream().filter(this::menuItemVisible).toList();
    }

    /** The view whose heading shows, or null. */
    String currentView() {
        final String text = pageText();
        return VIEW_TITLE.entrySet().stream().filter(e -> text.contains(e.getValue())).map(Map.Entry::getKey).findFirst().orElse(null);
    }

    /** Opens a view from the menu and waits until its own content is visible. */
    void goTo(final String view) {
        menuItem(view).click();
        waitText(VIEW_TITLE.get(view), 15_000);
        for (final String t : VIEW_CONTENT.getOrDefault(view, List.of())) {
            waitText(t, 15_000);
        }
        settle();
    }

    // ---- user menu

    /** The user menu (avatar and tenant at the top of the side menu). */
    Locator userMenu() {
        return page.getByRole(AriaRole.MENUITEM, new Page.GetByRoleOptions().setName(Pattern.compile("DEFAULT"))).first();
    }

    String userMenuText() {
        return textOf(userMenu());
    }

    void openUserMenu() {
        userMenu().click();
        settle(400);
    }

    List<String> userMenuEntries() {
        final Locator items = page.getByRole(AriaRole.MENUITEM);
        final List<String> out = new ArrayList<>();
        for (int i = 0, n = items.count(); i < n; i++) {
            final String t = textOf(items.nth(i));
            if (!t.isEmpty()) {
                out.add(t);
            }
        }
        return out;
    }

    /** Chooses Sign Out in the user menu; true when the login page shows then. */
    boolean signOut() {
        final Locator signOut = page.getByRole(AriaRole.MENUITEM, new Page.GetByRoleOptions().setName("Sign Out"));
        if (!signOut.isVisible()) {
            openUserMenu();
        }
        signOut.click();
        return waitLoginPage(15_000);
    }

    // ---- tooltips and notifications

    /** The tooltip a user sees when hovering {@code loc} (or the component inside it that has one), or "". */
    String tooltip(final Locator loc) {
        page.mouse().move(2, 2);
        page.waitForTimeout(250);
        loc.evaluate("el => el.scrollIntoView({ block: 'nearest', inline: 'center' })");
        // let a previous tooltip close, so its text is not read as this one's
        final Locator opened = page.locator("vaadin-tooltip-overlay[opened]:not([closing])");
        waitFor(() -> opened.count() == 0, 1500);
        final String tag = "tip" + (++tipSeq);
        final boolean inside = Boolean.TRUE.equals(loc.evaluate(MARK_TOOLTIP_TARGET, tag));
        final Locator hoverOn = inside ? page.locator("[data-e2e-tip=\"" + tag + "\"]") : loc;
        hoverOn.hover(new Locator.HoverOptions().setTimeout(5000).setForce(true));
        final String[] text = { "" };
        waitFor(() -> {
            final List<String> parts = new ArrayList<>();
            for (int i = 0, n = opened.count(); i < n; i++) {
                parts.add(textOf(opened.nth(i)));
            }
            text[0] = clean(String.join(" ", parts));
            return !text[0].isEmpty();
        }, 2500);
        page.mouse().move(2, 2);
        return text[0];
    }

    /** Every distinct notification text shown during the next {@code ms}. */
    List<String> notificationsDuring(final int ms) {
        final List<String> seen = new ArrayList<>();
        final long end = System.currentTimeMillis() + ms;
        while (System.currentTimeMillis() < end) {
            final Locator cards = page.locator("vaadin-notification-card");
            for (int i = 0, n = cards.count(); i < n; i++) {
                String t;
                try {
                    t = textOf(cards.nth(i));
                } catch (final PlaywrightException e) {
                    t = ""; // closed meanwhile
                }
                if (!t.isEmpty() && !seen.contains(t)) {
                    seen.add(t);
                }
            }
            page.waitForTimeout(150);
        }
        return seen;
    }

    /**
     * Test users' passwords from a {@code KEY=value} file (the env scripts' {@code APP_ENV_USERS_ENV}): key
     * {@code MIG_TU_<USER>_PW}, the user name in upper case with '-' as '_'. Read at run time, never printed.
     */
    static final class TestUsers {

        private static final Pattern LINE = Pattern.compile("^\\s*([A-Za-z0-9_]+)\\s*[=:]\\s*(.*?)\\s*$");

        private TestUsers() {
        }

        static String password(final String user) {
            final String prop = System.getProperty("e2e.usersEnv", "").trim();
            final String file = prop.isEmpty() ? System.getenv().getOrDefault("APP_ENV_USERS_ENV", "") : prop;
            if (file.isEmpty()) {
                throw new IllegalStateException("No test-user file: set -De2e.usersEnv or APP_ENV_USERS_ENV");
            }
            final Path path = Paths.get(file.replaceFirst("^~(?=/)", System.getProperty("user.home")));
            final String key = "MIG_TU_" + user.toUpperCase(Locale.ROOT).replace('-', '_') + "_PW";
            final List<String> lines;
            try {
                lines = Files.readAllLines(path);
            } catch (final IOException e) {
                throw new UncheckedIOException("Test-user file not readable: " + path, e);
            }
            for (final String line : lines) {
                final Matcher m = LINE.matcher(line);
                if (m.matches() && m.group(1).equals(key)) {
                    return m.group(2).replaceAll("^(['\"])(.*)\\1$", "$2");
                }
            }
            throw new IllegalStateException("No key " + key + " in the test-user file " + path);
        }
    }
}
