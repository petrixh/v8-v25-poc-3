/**
 * Copyright (c) 2023 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.hawkbit.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.eclipse.hawkbit.ui.components.NotificationUnreadButton;
import org.eclipse.hawkbit.ui.error.ErrorView;
import org.eclipse.hawkbit.ui.menu.DashboardMenu;
import org.eclipse.hawkbit.ui.menu.DashboardMenuItem;
import org.eclipse.hawkbit.ui.push.EventPushStrategy;
import org.eclipse.hawkbit.ui.push.UIEventProvider;
import org.eclipse.hawkbit.ui.utils.VaadinMessageSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.vaadin.spring.events.EventBus.UIEventBus;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.dom.Element;
import com.vaadin.flow.function.DeploymentConfiguration;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.Location;
import com.vaadin.flow.router.NotFoundException;
import com.vaadin.flow.router.QueryParameters;
import com.vaadin.flow.server.ErrorHandler;
import com.vaadin.flow.server.VaadinSession;

import io.qameta.allure.Description;
import io.qameta.allure.Feature;
import io.qameta.allure.Story;

/**
 * 1B-60: the hawkBit layout (the app shell) is built only for a logged-in user. Without one (an error view of an
 * unknown address below /UI/login/ reaches the layout for anyone) it shows the routed content only: no menu, no
 * header, no push, and it needs no user or tenant.
 */
@Feature("Unit Tests - Management UI")
@Story("Shell for an anonymous request")
class AnonymousShellTest {

    private final EventPushStrategy pushStrategy = mock(EventPushStrategy.class);
    private final DashboardMenu dashboardMenu = mock(DashboardMenu.class);
    private final NotificationUnreadButton notificationUnreadButton = mock(NotificationUnreadButton.class);
    // Vaadin holds the current UI weakly: keep a reference
    private final UI ui = new UI();

    @BeforeEach
    void setUp() {
        UI.setCurrent(ui);
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        UI.setCurrent(null);
        VaadinSession.setCurrent(null);
        SecurityContextHolder.clearContext();
    }

    private AbstractHawkbitUI newLayout() {
        return new AbstractHawkbitUI(pushStrategy, mock(UIEventBus.class), mock(UIEventProvider.class),
                mock(ApplicationContext.class), dashboardMenu, notificationUnreadButton, new UiProperties(),
                mock(VaadinMessageSource.class), mock(ErrorHandler.class)) {
            private static final long serialVersionUID = 1L;
        };
    }

    private static void logIn() {
        SecurityContextHolder.getContext()
                .setAuthentication(new TestingAuthenticationToken("user", "pw", "ROLE_USER"));
    }

    private static BeforeEnterEvent navigationTo(final Class<?> target, final String location) {
        final BeforeEnterEvent event = mock(BeforeEnterEvent.class);
        doReturn(target).when(event).getNavigationTarget();
        when(event.getLocation()).thenReturn(new Location(location));
        return event;
    }

    private static void assertNavigationUnchanged(final BeforeEnterEvent event) {
        verify(event, never()).rerouteToError(any());
        verify(event, never()).forwardToUrl(anyString());
    }

    private void assertContentOnly(final AbstractHawkbitUI layout) {
        assertThat(layout.getChildren()).hasSize(1);
        verify(dashboardMenu, never()).init();
        verifyNoInteractions(pushStrategy, notificationUnreadButton);
    }

    @Test
    @Description("No authentication at all (no security context): content only, the menu is not built")
    void noAuthentication() {
        assertContentOnly(newLayout());
    }

    @Test
    @Description("An anonymous token: content only, the menu is not built")
    void anonymousToken() {
        SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken("key", "anonymousUser",
                AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));
        assertContentOnly(newLayout());
    }

    @Test
    @Description("A token that is not authenticated: content only, the menu is not built")
    void unauthenticatedToken() {
        final TestingAuthenticationToken token = new TestingAuthenticationToken("user", "pw", List.of());
        token.setAuthenticated(false);
        SecurityContextHolder.getContext().setAuthentication(token);
        assertContentOnly(newLayout());
    }

    @Test
    @Description("A logged-in user: the shell path is taken (the push strategy is initialised first), as before")
    void loggedInUserGetsShell() {
        SecurityContextHolder.getContext()
                .setAuthentication(new TestingAuthenticationToken("user", "pw", "ROLE_USER"));
        try {
            newLayout();
        } catch (final RuntimeException e) {
            // the rest of the shell needs a Vaadin session, which a unit test has not: only the entry is checked
        }
        verify(pushStrategy).init(any(UI.class));
    }

    @Test
    @Description("R1: a layout built without the shell and still anonymous sends any view to the error view")
    void noShellAnonymousViewGoesToErrorView() {
        final AbstractHawkbitUI layout = newLayout();
        final BeforeEnterEvent event = navigationTo(Div.class, "rollout");

        layout.beforeEnter(event);

        verify(event).rerouteToError(NotFoundException.class);
        verify(event, never()).forwardToUrl(anyString());
        verifyNoInteractions(dashboardMenu);
    }

    @Test
    @Description("R1: a layout built without the shell, after the session logged in elsewhere, loads the address as a "
            + "new page (whose new layout has the shell and checks the view); the view is never shown in this layout")
    void noShellLoggedInViewLoadsNewPage() {
        final AbstractHawkbitUI layout = newLayout();
        logIn();
        final BeforeEnterEvent event = navigationTo(Div.class, "rollout/1");
        when(event.getLocation()).thenReturn(new Location("rollout/1", QueryParameters.simple(Map.of("a", "b"))));

        layout.beforeEnter(event);

        verify(event).forwardToUrl("./rollout/1?a=b");
        verify(event, never()).rerouteToError(any());
        verifyNoInteractions(dashboardMenu);
    }

    @Test
    @Description("R1: the empty route is loaded as the UI root, never as an empty address")
    void noShellLoggedInEmptyRouteLoadsUiRoot() {
        final AbstractHawkbitUI layout = newLayout();
        logIn();
        final BeforeEnterEvent event = navigationTo(AbstractHawkbitUI.EmptyView.class, "");

        layout.beforeEnter(event);

        verify(event).forwardToUrl("./");
    }

    @Test
    @Description("R1: a layout built without the shell still shows the error view, logged in or not")
    void noShellErrorViewUnchanged() {
        final AbstractHawkbitUI layout = newLayout();
        final BeforeEnterEvent anonymous = navigationTo(ErrorView.class, "login/zzz");
        layout.beforeEnter(anonymous);
        assertNavigationUnchanged(anonymous);

        logIn();
        final BeforeEnterEvent loggedIn = navigationTo(ErrorView.class, "login/zzz");
        layout.beforeEnter(loggedIn);
        assertNavigationUnchanged(loggedIn);
    }

    @Test
    @Description("A layout with the shell: a menu view the user may not open goes to the error view, an allowed one "
            + "opens (unchanged)")
    void shellChecksMenuPermission() {
        logIn();
        final VaadinSession session = mock(VaadinSession.class);
        when(session.getLocale()).thenReturn(Locale.ENGLISH);
        when(session.hasLock()).thenReturn(true);
        when(session.getConfiguration()).thenReturn(mock(DeploymentConfiguration.class));
        VaadinSession.setCurrent(session);
        ui.getInternals().setSession(session);
        when(dashboardMenu.getElement()).thenReturn(new Element("div"));
        when(notificationUnreadButton.getElement()).thenReturn(new Element("div"));
        final DashboardMenuItem rollout = mock(DashboardMenuItem.class);
        when(dashboardMenu.getByViewName("rollout")).thenReturn(rollout);
        when(dashboardMenu.isAccessDenied("rollout")).thenReturn(true);
        when(dashboardMenu.getByViewName("deployment")).thenReturn(mock(DashboardMenuItem.class));
        when(dashboardMenu.isAccessDenied("deployment")).thenReturn(false);
        final AbstractHawkbitUI layout = newLayout();
        verify(dashboardMenu).init();

        final BeforeEnterEvent denied = navigationTo(Div.class, "rollout");
        layout.beforeEnter(denied);
        verify(denied).rerouteToError(NotFoundException.class);
        verify(denied, never()).forwardToUrl(anyString());

        final BeforeEnterEvent allowed = navigationTo(Div.class, "deployment");
        layout.beforeEnter(allowed);
        assertNavigationUnchanged(allowed);
    }
}
