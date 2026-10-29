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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import com.microsoft.playwright.PlaywrightException;
import org.junit.jupiter.api.Test;

/**
 * Log in, look around and sign out, as the administrator, from the app's seed state (workflow WF01 of the migration:
 * the login page, the start view and menu, the user menu, every view with its seed content, sign-out, and no way back
 * in without signing in).
 */
class Wf01LoginLogoutIT extends AbstractUiE2eTest {

    private static final String USER = "admin";

    @Test
    void logInLookAroundAndSignOut() {
        step(1, "the start address shows the login page, with no tenant field", () -> {
            ui.openAddress("start");
            assertTrue(ui.waitLoginPage(15_000), "login page with username, password and sign-in button");
            assertFalse(ui.hasTenantField(), "tenant field");
        });

        step(2, "signing in as admin opens the Deployment view with the full menu", () -> {
            ui.login(USER);
            assertEquals("Deployment", ui.currentView(), "start view");
            assertEquals(HawkbitUi.VIEWS, ui.menuEntries(), "menu");
        });

        step(3, "the user menu shows the tenant DEFAULT, names the user in its tooltip and offers Sign Out", () -> {
            assertEquals("DEFAULT", ui.userMenuText(), "user menu caption");
            final String tip = ui.tooltip(ui.userMenu());
            assertTrue(tip.contains("Tenant: DEFAULT") && Pattern.compile("User: admin\\b").matcher(tip).find(),
                    "user menu tooltip names tenant and user: " + tip);
            ui.openUserMenu();
            assertTrue(ui.userMenuEntries().contains("Sign Out"), "Sign Out offered: " + ui.userMenuEntries());
        });

        step(4, "each view opens from the menu with its seed content and no error notification", () -> {
            page.keyboard().press("Escape");
            ui.settle(300);
            final Map<String, List<String>> expected = new LinkedHashMap<>();
            expected.put("Rollout", List.of("Rollouts", "Distribution set", "Status"));
            expected.put("Target Filters", List.of("Seed Lab devices"));
            expected.put("Distributions", List.of("Seed OS Base", "Seed Bundle", "Seed Apps", "Seed Agent"));
            expected.put("Upload", List.of("Seed OS", "Seed App", "Seed Agent", "Drop Files to upload"));
            expected.put("System Config", List.of("Distribution Configuration", "Repository Configuration", "Rollout Configuration",
                    "Authentication Configuration"));
            expected.put("Deployment", List.of("Seed Device 01", "Seed Device 10", "Seed Bundle"));
            for (final Map.Entry<String, List<String>> view : expected.entrySet()) {
                ui.goTo(view.getKey());
                final List<String> notifications = ui.notificationsDuring(1500);
                final String text = ui.pageText();
                assertEquals(view.getKey(), ui.currentView(), "view opened from the menu");
                for (final String s : view.getValue()) {
                    assertTrue(text.contains(s), view.getKey() + " shows '" + s + "'");
                }
                assertEquals(List.of(), notifications, view.getKey() + ": notifications");
            }
        });

        step(5, "Sign Out shows the login page", () -> assertTrue(ui.signOut(), "login page after Sign Out"));

        step(6, "going back in the browser shows the login page, not the app", () -> {
            // Going back may end in a redirect to the login page, which Playwright can report as an interrupted
            // navigation: that is logged with its reason, not ignored, and the outcome a user sees is checked below
            // (the workflow's replay script does the same).
            final String before = page.url();
            try {
                page.goBack();
            } catch (final PlaywrightException e) {
                System.out.println("Step 6: going back from " + before + " ended with: " + e.getMessage().split("\n")[0]);
            }
            System.out.println("Step 6: went back from " + before + " to " + page.url());
            ui.settle(2000);
            assertTrue(ui.waitLoginPage(5000), "login page after going back");
            assertFalse(ui.menuItemVisible("Deployment"), "main menu shown");
        });

        step(7, "the Deployment view's address shows the login page instead of the view", () -> {
            ui.openAddress("Deployment");
            assertTrue(ui.waitLoginPage(5000), "login page at the view's address");
            assertFalse(ui.menuItemVisible("Deployment"), "main menu shown");
        });
    }
}
