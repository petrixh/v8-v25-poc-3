/**
 * Copyright (c) 2023 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.hawkbit.ui.login;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Stream;

import org.eclipse.hawkbit.im.authentication.MultitenancyIndicator;
import org.eclipse.hawkbit.ui.UiProperties;
import org.eclipse.hawkbit.ui.utils.VaadinMessageSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.core.io.ByteArrayResource;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.Location;
import com.vaadin.flow.server.VaadinSession;
import com.vaadin.flow.server.WebBrowser;

import io.qameta.allure.Description;
import io.qameta.allure.Feature;
import io.qameta.allure.Story;

/**
 * Flow reuses the login route's instance when the login view is entered again
 * in the same UI (browser back or forward, an in-app link). Vaadin 8 built the
 * login UI once per UI; the view must still work after it is entered again.
 */
@Feature("Unit Tests - Management UI")
@Story("Login view")
class AbstractHawkbitLoginUITest {

    private UI ui;

    @BeforeEach
    void currentUiAndSession() {
        final VaadinSession session = mock(VaadinSession.class);
        when(session.hasLock()).thenReturn(true);
        when(session.getLocale()).thenReturn(Locale.ENGLISH);
        when(session.getBrowser()).thenReturn(mock(WebBrowser.class));
        ui = new UI();
        ui.getInternals().setSession(session);
        VaadinSession.setCurrent(session);
        UI.setCurrent(ui);
    }

    @AfterEach
    void cleanUp() {
        UI.setCurrent(null);
        VaadinSession.setCurrent(null);
    }

    @Test
    @Description("Sign in follows the fields after the login view is entered once")
    void signInFollowsFieldsAfterFirstEntry() {
        final TestLoginUI view = newLoginView();
        view.beforeEnter(enterEvent("login"));

        assertSignInFollowsFields(view);
    }

    @Test
    @Description("Sign in still follows the fields after the login view is entered again in the same UI")
    void signInFollowsFieldsAfterReentry() {
        final TestLoginUI view = newLoginView();
        view.beforeEnter(enterEvent("login"));
        view.beforeEnter(enterEvent("login?user=admin"));
        view.beforeEnter(enterEvent("login"));

        assertThat(view.getChildren().count()).as("one login form, not one per entry")
                .isEqualTo(newEnteredOnce().getChildren().count());
        assertSignInFollowsFields(view);
    }

    private void assertSignInFollowsFields(final TestLoginUI view) {
        final Button signIn = signIn(view);
        assertThat(signIn.isEnabled()).as("sign in with empty fields").isFalse();

        view.getUsername().setValue("admin");
        view.getPassword().setValue("secret");
        assertThat(signIn.isEnabled()).as("sign in with user and password").isTrue();

        view.getPassword().clear();
        assertThat(signIn.isEnabled()).as("sign in after the password is cleared").isFalse();
    }

    private TestLoginUI newEnteredOnce() {
        final TestLoginUI view = newLoginView();
        view.beforeEnter(enterEvent("login"));
        return view;
    }

    private static TestLoginUI newLoginView() {
        final ApplicationContext context = mock(ApplicationContext.class);
        when(context.getResource(anyString()))
                .thenReturn(new ByteArrayResource("<p>footer</p>".getBytes(StandardCharsets.UTF_8)));
        final VaadinMessageSource i18n = mock(VaadinMessageSource.class);
        when(i18n.getMessage(anyString(), any(Object[].class))).thenReturn("text");
        final MultitenancyIndicator multiTenancy = mock(MultitenancyIndicator.class);
        return new TestLoginUI(context, mock(VaadinLoginSupport.class), i18n, new UiProperties(), multiTenancy);
    }

    private static BeforeEnterEvent enterEvent(final String location) {
        final BeforeEnterEvent event = mock(BeforeEnterEvent.class);
        when(event.getLocation()).thenReturn(new Location(location));
        return event;
    }

    private static Button signIn(final Component root) {
        return descendants(root).filter(Button.class::isInstance).map(Button.class::cast)
                .filter(button -> Optional.of("login-signin").equals(button.getId())).findFirst().orElseThrow();
    }

    private static Stream<Component> descendants(final Component root) {
        return Stream.concat(Stream.of(root), root.getChildren().flatMap(AbstractHawkbitLoginUITest::descendants));
    }

    private static final class TestLoginUI extends AbstractHawkbitLoginUI {
        private static final long serialVersionUID = 1L;

        private TestLoginUI(final ApplicationContext context, final VaadinLoginSupport vaadinSecurity,
                final VaadinMessageSource i18n, final UiProperties uiProperties,
                final MultitenancyIndicator multiTenancyIndicator) {
            super(context, vaadinSecurity, i18n, uiProperties, multiTenancyIndicator);
        }
    }
}
