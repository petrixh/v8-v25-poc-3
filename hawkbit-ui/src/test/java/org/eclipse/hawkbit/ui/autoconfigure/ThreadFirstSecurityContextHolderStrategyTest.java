/**
 * Copyright (c) 2023 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.hawkbit.ui.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;

import com.vaadin.flow.server.VaadinSession;
import com.vaadin.flow.server.WrappedSession;

import io.qameta.allure.Description;
import io.qameta.allure.Feature;
import io.qameta.allure.Story;

@Feature("Unit Tests - Management UI")
@Story("Security context holder strategy")
class ThreadFirstSecurityContextHolderStrategyTest {

    private final ThreadFirstSecurityContextHolderStrategy strategy = new ThreadFirstSecurityContextHolderStrategy();

    @AfterEach
    void cleanUp() {
        VaadinSession.setCurrent(null);
        strategy.clearContext();
    }

    @Test
    @Description("A context switched for a block wins over the Vaadin session's user and is gone once the session's context is restored")
    void switchedContextWinsOverVaadinSessionUntilRestored() {
        final WrappedSession httpSession = currentVaadinSession();
        final SecurityContext userA = context("userA");
        setSessionContext(httpSession, userA);

        final SecurityContext old = strategy.getContext();
        assertThat(old).isSameAs(userA);

        final SecurityContext system = context("system");
        strategy.setContext(system);
        assertThat(strategy.getContext()).isSameAs(system);

        strategy.setContext(old);
        assertThat(strategy.getContext()).isSameAs(userA);

        // nothing left on the thread: a later session user is answered
        final SecurityContext userB = context("userB");
        setSessionContext(httpSession, userB);
        assertThat(strategy.getContext()).isSameAs(userB);
    }

    @Test
    @Description("Nested switches restore the outer switched context, then the session's")
    void nestedSwitchesRestoreInOrder() {
        final WrappedSession httpSession = currentVaadinSession();
        final SecurityContext user = context("user");
        setSessionContext(httpSession, user);

        final SecurityContext outer = context("outer");
        final SecurityContext inner = context("inner");
        strategy.setContext(outer);
        strategy.setContext(inner);
        assertThat(strategy.getContext()).isSameAs(inner);
        strategy.setContext(outer);
        assertThat(strategy.getContext()).isSameAs(outer);
        strategy.setContext(user);
        assertThat(strategy.getContext()).isSameAs(user);
    }

    @Test
    @Description("Without a Vaadin session the strategy is a plain thread-local holder")
    void withoutVaadinSessionActsAsThreadLocal() {
        assertThat(strategy.getContext().getAuthentication()).isNull();

        final SecurityContext context = context("user");
        strategy.setContext(context);
        assertThat(strategy.getContext()).isSameAs(context);

        strategy.clearContext();
        assertThat(strategy.getContext().getAuthentication()).isNull();
    }

    @Test
    @Description("A context set on the thread is cleared by clearContext also while a Vaadin session is current")
    void clearContextRemovesSwitchedContext() {
        final WrappedSession httpSession = currentVaadinSession();
        final SecurityContext user = context("user");
        setSessionContext(httpSession, user);

        strategy.setContext(context("system"));
        strategy.clearContext();
        assertThat(strategy.getContext()).isSameAs(user);
    }

    private static WrappedSession currentVaadinSession() {
        final VaadinSession vaadinSession = mock(VaadinSession.class);
        final WrappedSession httpSession = mock(WrappedSession.class);
        when(vaadinSession.getSession()).thenReturn(httpSession);
        VaadinSession.setCurrent(vaadinSession);
        return httpSession;
    }

    private static void setSessionContext(final WrappedSession httpSession, final SecurityContext context) {
        when(httpSession.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY))
                .thenReturn(context);
    }

    private static SecurityContext context(final String name) {
        return new SecurityContextImpl(new TestingAuthenticationToken(name, null));
    }
}
