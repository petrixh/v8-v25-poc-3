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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.List;

import org.eclipse.hawkbit.ui.components.NotificationUnreadButton;
import org.eclipse.hawkbit.ui.menu.DashboardMenu;
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
import com.vaadin.flow.server.ErrorHandler;

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
        SecurityContextHolder.clearContext();
    }

    private AbstractHawkbitUI newLayout() {
        return new AbstractHawkbitUI(pushStrategy, mock(UIEventBus.class), mock(UIEventProvider.class),
                mock(ApplicationContext.class), dashboardMenu, notificationUnreadButton, new UiProperties(),
                mock(VaadinMessageSource.class), mock(ErrorHandler.class)) {
            private static final long serialVersionUID = 1L;
        };
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
}
