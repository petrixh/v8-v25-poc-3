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

import java.util.List;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.PlaywrightException;
import org.junit.jupiter.api.Test;

/**
 * Wrong credentials on the login page: a wrong password and an unknown user name are refused with the same
 * notification, an empty password cannot be submitted, repeated failures do not lock the account, and the right
 * password then signs in. (Workflow WF02 of the migration's catalogue.)
 */
class LoginRefusedIT extends AbstractUiE2eTest {

    private static final String USER = "admin";
    private static final String FAILED = "Login failed! Login with the given credentials failed.";
    // Wrong credentials made up here; they are not the test users' secrets.
    private static final String WRONG_PW = "e2e-wrong-password";
    private static final String UNKNOWN_USER = "e2e-no-such-user";

    /** No notification left from the previous attempt (they close by themselves). */
    private void noNotification() {
        assertTrue(ui.dismissNotifications(20_000), "a notification from the previous attempt did not go away");
    }

    /** One sign-in attempt; the notifications it shows. */
    private List<String> attempt(final String user, final String password) {
        noNotification();
        ui.usernameField().fill(user);
        ui.passwordField().fill(password);
        ui.settle(300);
        return ui.notificationsDuring(() -> ui.signInButton().click(), 6000, s -> !s.isEmpty());
    }

    @Test
    void wrongCredentialsAreRefusedWithoutLockout() {
        step(1, "admin with a wrong password: \"Login failed!\", the login page stays", () -> {
            ui.openAddress("login");
            assertTrue(ui.waitLoginPage(15_000), "login page");
            assertEquals(List.of(FAILED), attempt(USER, WRONG_PW), "notifications");
            assertTrue(ui.isLoginPage(), "still the login page, with both fields");
        });

        step(2, "an unknown user name: the same notification, still the login page", () -> {
            assertEquals(List.of(FAILED), attempt(UNKNOWN_USER, WRONG_PW), "notifications");
            assertTrue(ui.isLoginPage(), "still the login page");
        });

        step(3, "admin with an empty password: sign-in disabled, nothing happens", () -> {
            noNotification();
            ui.usernameField().fill(USER);
            ui.passwordField().fill("");
            ui.settle(500);
            final Locator signIn = ui.signInButton();
            assertFalse(signIn.isEnabled(), "sign-in button enabled");
            // A user clicks the disabled button anyway, and presses Enter in the password field.
            final List<String> notes = ui.notificationsDuring(() -> {
                try {
                    signIn.click(new Locator.ClickOptions().setForce(true).setTimeout(2000));
                } catch (final PlaywrightException e) {
                    // disabled: the click does not go through
                }
                ui.passwordField().press("Enter");
            }, 4000, null);
            assertEquals(List.of(), notes, "notifications");
            assertTrue(ui.isLoginPage(), "still the login page");
        });

        step(4, "two more wrong passwords: the same notification each time, no lockout", () -> {
            for (final int i : new int[] { 1, 2 }) {
                assertEquals(List.of(FAILED), attempt(USER, WRONG_PW + i), "notifications of attempt " + i);
            }
            assertTrue(ui.isLoginPage(), "still the login page");
        });

        step(5, "the right password opens the Deployment view", () -> {
            noNotification();
            ui.login(USER);
            assertEquals("Deployment", ui.currentView(), "view after signing in");
        });
    }
}
