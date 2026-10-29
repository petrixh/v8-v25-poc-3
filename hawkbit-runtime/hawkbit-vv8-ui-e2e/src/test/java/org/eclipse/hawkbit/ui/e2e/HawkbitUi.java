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
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.microsoft.playwright.ElementHandle;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.LoadState;

/**
 * The hawkBit UI as a user sees it: login, main menu, views, user menu, tooltips, notifications, dialogs, form
 * fields, details tabs (the lists are in {@link Grids}). Locators are user-level (roles, labels, placeholders, visible
 * text); icon-only buttons, which have no accessible name, are found by the component ids the app gives them. Where
 * Flow components keep their text in light DOM slots (menu items, tooltips, notification cards), the text is read
 * through the slots. Mostly the public tags and attributes of Vaadin components are used ({@code vaadin-tooltip},
 * {@code vaadin-tooltip-overlay[opened]}, {@code vaadin-notification-card}, a dialog's {@code opened}).
 * Five things are not public API and can change in a Vaadin update without notice: the Flow client objects in
 * {@code window.Vaadin.Flow.clients} and their {@code isActive()} (the check "no server round trip in flight", as
 * TestBench uses it), the {@code closing} state attribute and the {@code positionTarget} property of the tooltip
 * overlay, the {@code target} property of {@code vaadin-tooltip}, and the {@code scrolling} attribute of the grid's
 * {@code #scroller} (a grid ignores the pointer while it scrolls). If a test starts to hang or read the wrong tooltip
 * after a Vaadin update, look there first.
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
     * shadow tree). Tooltip elements are left out: their text is not on screen. When the element is itself a field, its
     * caption (its {@code label} slot, or the label of a wrapper that holds just the caption and the field) is not part of
     * its text; the captions of fields inside it (a dialog, a form) are.
     */
    private static final String FLAT_TEXT = "(el) => { const kids = [...el.children];"
            + " const captioned = kids.length === 2 && kids[0].tagName === 'LABEL'; const ft = (n) => {"
            + " if (captioned && n === kids[0]) return '';"
            + " if (n.nodeType === 3) return n.textContent; if (n.nodeType !== 1) return '';"
            + " if (n.hidden || getComputedStyle(n).display === 'none' || n.tagName === 'VAADIN-TOOLTIP') return '';"
            + " if (n.tagName === 'SLOT' && n.name === 'label' && n.getRootNode().host === el) return '';"
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
        return waitFor(condition, ms, 250);
    }

    /** Polls {@code condition} every {@code every} ms until it holds or {@code ms} have passed; returns its last value. */
    boolean waitFor(final BooleanSupplier condition, final int ms, final int every) {
        return Boolean.TRUE.equals(waitForValue(() -> condition.getAsBoolean(), ms, every));
    }

    /**
     * Polls {@code value} every {@code every} ms until it gives a value that counts as found (not null, not false, not
     * empty) or {@code ms} have passed; returns its last value. A runtime exception of {@code value} (an element not
     * there yet) counts as not found.
     */
    <T> T waitForValue(final Supplier<T> value, final int ms, final int every) {
        final long end = System.currentTimeMillis() + ms;
        while (true) {
            T v;
            try {
                v = value.get();
            } catch (final RuntimeException e) {
                v = null;
            }
            if (found(v) || System.currentTimeMillis() >= end) {
                return v;
            }
            page.waitForTimeout(every);
        }
    }

    private static boolean found(final Object v) {
        return v != null && !Boolean.FALSE.equals(v) && !(v instanceof String s && s.isEmpty())
                && !(v instanceof Collection<?> c && c.isEmpty());
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

    /** As {@link #textOf(Locator)}, waiting at most {@code ms} for the element. */
    static String textOf(final Locator loc, final int ms) {
        return clean((String) loc.evaluate(FLAT_TEXT, null, new Locator.EvaluateOptions().setTimeout(ms)));
    }

    /** The visible text of each element {@code loc} matches. */
    static List<String> textsOf(final Locator loc) {
        final List<String> out = new ArrayList<>();
        for (int i = 0, n = loc.count(); i < n; i++) {
            out.add(textOf(loc.nth(i)));
        }
        return out;
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

    Locator usernameField() {
        return page.getByLabel("Username");
    }

    Locator signInButton() {
        return page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Sign in"));
    }

    Locator passwordField() {
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
        return page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("^\\W*" + escape(name) + "\\s*$")))
                .first();
    }

    /**
     * {@code s} as a literal in a regular expression that Playwright evaluates in the browser (JavaScript syntax:
     * {@code Pattern.quote}'s {@code \Q...\E} does not work there).
     */
    static String escape(final String s) {
        return s.replaceAll("[.*+?^${}()|\\[\\]\\\\]", "\\\\$0");
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
        return tooltip(loc, 2500);
    }

    /**
     * The tooltip a user sees when hovering {@code loc} (or the component inside it that has one) within {@code ms}, or
     * "". A disabled button takes no pointer events, so no tooltip shows on it: that is "no tooltip", not a failure.
     */
    String tooltip(final Locator loc, final int ms) {
        page.mouse().move(2, 2);
        page.waitForTimeout(250);
        // the target in the middle of the window (the pointer stopping on a window edge does not open a tooltip)...
        try {
            loc.evaluate("el => el.scrollIntoView({ block: 'nearest', inline: 'center' })");
        } catch (final PlaywrightException e) {
            // not there: the hover below says so
        }
        // ...a previous tooltip closed, so its text is not read as this one's...
        waitFor(() -> page.locator("vaadin-tooltip-overlay[opened]").count() == 0, 1500, 100);
        // ...and no grid still scrolling: a grid ignores the pointer over its cells while it scrolls
        page.waitForTimeout(300);
        waitFor(() -> page.locator("vaadin-grid #scroller[scrolling]").count() == 0, 3000, 100);
        final Locator hoverOn = tooltipTarget(loc);
        try {
            hover(hoverOn);
        } catch (final PlaywrightException e) {
            if (hoverOn.isVisible()) {
                return "";
            }
            throw e;
        }
        String text = waitForValue(() -> shownTooltip(hoverOn), ms, 150);
        if (text == null || text.isEmpty()) {
            // The first hover right after a dialog closed or a grid scrolled can read nothing: hover once more.
            page.mouse().move(2, 2);
            page.waitForTimeout(400);
            try {
                hover(hoverOn);
                text = waitForValue(() -> shownTooltip(hoverOn), Math.min(ms, 1500), 150);
            } catch (final PlaywrightException e) {
                // no second read
            }
        }
        page.mouse().move(2, 2);
        return text == null ? "" : text;
    }

    private static void hover(final Locator loc) {
        // force: a spinning icon (a running status) is never "stable" for Playwright, though the pointer can rest on it
        loc.hover(new Locator.HoverOptions().setTimeout(5000).setForce(true));
    }

    /** The element whose tooltip a user gets when hovering {@code loc}: the component inside it that has one, else itself. */
    private Locator tooltipTarget(final Locator loc) {
        final String tag = "tip" + (++tipSeq);
        boolean inside;
        try {
            inside = Boolean.TRUE.equals(loc.evaluate(MARK_TOOLTIP_TARGET, tag));
        } catch (final PlaywrightException e) {
            inside = false;
        }
        return inside ? page.locator("[data-e2e-tip=\"" + tag + "\"]") : loc;
    }

    /**
     * The text of the tooltip open for {@code hoverOn} (the overlay positioned at it, or at what holds it or what it
     * holds), else of the last opened one; null when none is open.
     */
    private String shownTooltip(final Locator hoverOn) {
        final Locator overlays = page.locator("vaadin-tooltip-overlay[opened]:not([closing])");
        final int n = overlays.count();
        if (n == 0) {
            return null;
        }
        int pick = n - 1;
        ElementHandle el = null;
        try {
            el = hoverOn.elementHandle(new Locator.ElementHandleOptions().setTimeout(1000));
            for (int i = n - 1; i >= 0; i--) {
                if (Boolean.TRUE.equals(overlays.nth(i).evaluate(
                        "(o, e) => { const t = o.positionTarget; return !!t && (t === e || t.contains(e) || e.contains(t)); }", el))) {
                    pick = i;
                    break;
                }
            }
        } catch (final PlaywrightException e) {
            // the last opened one
        } finally {
            if (el != null) {
                el.dispose();
            }
        }
        return textOf(overlays.nth(pick));
    }

    /** Every distinct notification text shown during the next {@code ms}. */
    List<String> notificationsDuring(final int ms) {
        return notificationsDuring(() -> { }, ms, null);
    }

    /**
     * Every distinct notification text shown while {@code action} runs and up to {@code ms} after it, or until
     * {@code until} holds for the texts seen so far.
     */
    List<String> notificationsDuring(final Runnable action, final int ms, final Predicate<List<String>> until) {
        final List<String> seen = new ArrayList<>();
        action.run();
        final long end = System.currentTimeMillis() + ms;
        while (System.currentTimeMillis() < end) {
            final Locator cards = page.locator("vaadin-notification-card");
            for (int i = 0, n = cards.count(); i < n; i++) {
                String t;
                try {
                    t = textOf(cards.nth(i), 1000);
                } catch (final PlaywrightException e) {
                    t = ""; // closed meanwhile
                }
                if (!t.isEmpty() && !seen.contains(t)) {
                    seen.add(t);
                }
            }
            if (until != null && until.test(seen)) {
                break;
            }
            page.waitForTimeout(150);
        }
        return seen;
    }

    /** Waits up to {@code ms} until no notification is on screen (they close by themselves); false when one stays. */
    boolean dismissNotifications(final int ms) {
        return waitFor(() -> page.locator("vaadin-notification-card").count() == 0, ms, 400);
    }

    // ---- dialogs and form controls

    /** The dialog on top. */
    Locator dialog() {
        return page.getByRole(AriaRole.DIALOG).last();
    }

    String dialogText() {
        return textOf(dialog());
    }

    /** Whether a dialog is open (a closed one stays in the page while its closing animation runs). */
    boolean dialogOpen() {
        return ((Number) page.getByRole(AriaRole.DIALOG).evaluateAll("els => els.filter(e => e.opened !== false).length")).intValue() > 0;
    }

    /** Clicks the button {@code name} of the dialog on top. */
    void clickDialogButton(final String name) {
        dialog().getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName(name)).click();
    }

    /** Waits up to {@code ms} until no dialog is open. */
    boolean dialogClosed(final int ms) {
        return waitFor(() -> !dialogOpen(), ms);
    }

    /**
     * A button by its visible name. A tooltip that is open on the button adds its text to the accessible name ("Lab
     * Lab"), so the name may be repeated.
     */
    static Locator buttonNamed(final Locator scope, final String name) {
        return scope.getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions()
                .setName(Pattern.compile("^" + escape(name) + "(?:\\s+" + escape(name) + ")?\\s*$")));
    }

    /** As {@link #buttonNamed(Locator, String)}, anywhere on the page. */
    Locator buttonNamed(final String name) {
        return buttonNamed(page.locator("body"), name);
    }

    /** Picks {@code value} in the combo box labelled {@code label} in {@code scope}: opens its list, clicks the entry. */
    String pickFromCombo(final Locator scope, final String label, final String value) {
        final Locator f = scope.getByLabel(label);
        f.click();
        f.fill("");
        f.press("ArrowDown");
        settle(500);
        clickComboEntry(value);
        settle(300);
        return f.inputValue();
    }

    /** The input with the placeholder {@code ph} in {@code scope} (fields that have no caption). */
    Locator byPlaceholder(final Locator scope, final String ph) {
        // the placeholder is on the field component too: the match is narrowed to the input
        return scope.getByPlaceholder(ph, new Locator.GetByPlaceholderOptions().setExact(true)).and(page.locator("input, textarea"));
    }

    /** Picks {@code value} in the combo box with the placeholder {@code ph} in {@code scope}. */
    String pickByPlaceholder(final Locator scope, final String ph, final String value) {
        final Locator f = byPlaceholder(scope, ph);
        f.click();
        f.press("ArrowDown");
        settle(600);
        clickComboEntry(value);
        settle(600);
        return f.inputValue();
    }

    private void clickComboEntry(final String value) {
        page.getByRole(AriaRole.OPTION).filter(new Locator.FilterOptions().setHasText(Pattern.compile("^\\s*" + escape(value) + "\\s*$")))
                .first().click();
    }

    /** Chooses the radio option {@code name} in {@code scope} as a user does: clicks its label. */
    void chooseOption(final Locator scope, final String name) {
        scope.getByText(Pattern.compile("^\\W*" + escape(name) + "$")).first().click();
        settle(300);
    }

    // ---- details tab sheets

    /** The details panel of the selected entity ({@code target}, {@code distributionset}, {@code softwaremodule}). */
    Locator details(final String what) {
        return page.locator("[id=\"" + what + ".details.tabsheet\"]").first();
    }

    /** Opens the tab {@code name} of a tab sheet. */
    void openTab(final Locator sheet, final String name) {
        sheet.getByRole(AriaRole.TAB, new Locator.GetByRoleOptions().setName(name).setExact(true))
                .click(new Locator.ClickOptions().setTimeout(10_000));
        settle(300);
    }

    /** The visible text of the open tab of a tab sheet. */
    static String tabText(final Locator sheet) {
        return textOf(sheet.getByRole(AriaRole.TABPANEL).first());
    }

    /** The text of the first text area in the open tab of a tab sheet (a description). */
    static String tabTextArea(final Locator sheet) {
        return sheet.getByRole(AriaRole.TABPANEL).first().locator("textarea").first().inputValue();
    }

    /**
     * Test users' passwords from a {@code KEY=value} file (the env scripts' {@code APP_ENV_USERS_ENV}): key
     * {@code MIG_TU_<USER>_PW}, the user name in upper case with '-' as '_'. Read at run time, never printed.
     */
    static final class TestUsers {

        private static final Pattern LINE = Pattern.compile("^\\s*([A-Za-z0-9_]+)\\s*[=:]\\s*(.*?)\\s*$");
        /** Every password handed out, so that failure messages can be cleared of them ({@link #redact}). */
        private static final Set<String> HANDED_OUT = ConcurrentHashMap.newKeySet();

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
                    final String pw = m.group(2).replaceAll("^(['\"])(.*)\\1$", "$2");
                    if (!pw.isEmpty()) {
                        HANDED_OUT.add(pw);
                    }
                    return pw;
                }
            }
            throw new IllegalStateException("No key " + key + " in the test-user file " + path);
        }

        /** {@code text} with every password handed out so far replaced by "***". */
        static String redact(final String text) {
            String out = text == null ? "" : text;
            for (final String pw : HANDED_OUT) {
                out = out.replace(pw, "***");
            }
            return out;
        }
    }
}
