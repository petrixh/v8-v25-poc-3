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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextHolderFilter;

import com.vaadin.flow.server.VaadinSession;
import com.vaadin.flow.server.WrappedSession;

import io.qameta.allure.Description;
import io.qameta.allure.Feature;
import io.qameta.allure.Story;

@Feature("Unit Tests - Management UI")
@Story("Security context holder strategy")
class ThreadFirstSecurityContextHolderStrategyTest {

    private final ThreadFirstSecurityContextHolderStrategy strategy = new ThreadFirstSecurityContextHolderStrategy();

    // Flow keeps the current VaadinSession only weakly: hold the mocks for the
    // whole test, or the garbage collector may drop them mid-test
    private final List<VaadinSession> vaadinSessions = new ArrayList<>();

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

    @Test
    @Description("1B-29: a re-login of the same browser session during a switch leaves the old user on the request thread only until the request ends")
    void contextLeftByReloginDuringSwitchEndsWithTheRequest() throws Exception {
        final SecurityContextHolderFilter filter = new SecurityContextHolderFilter(
                new HttpSessionSecurityContextRepository());
        filter.setSecurityContextHolderStrategy(strategy);
        final MockHttpServletRequest request = new MockHttpServletRequest();
        final MockHttpSession session = new MockHttpSession();
        request.setSession(session);
        final SecurityContext userA = context("userA");
        final SecurityContext userB = context("userB");
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, userA);
        final AtomicReference<String> afterSwitch = new AtomicReference<>();

        filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> {
            final WrappedSession httpSession = currentVaadinSession();
            when(httpSession.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY))
                    .thenAnswer(invocation -> session
                            .getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY));
            try {
                // as SystemSecurityContext.runAsSystem
                final SecurityContext before = strategy.getContext();
                strategy.setContext(context("system"));
                try {
                    // another tab of the same browser session logs in as userB
                    session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, userB);
                } finally {
                    strategy.setContext(before);
                }
                afterSwitch.set(strategy.getContext().getAuthentication().getName());
            } finally {
                // the Vaadin servlet ends the request
                VaadinSession.setCurrent(null);
            }
        });

        assertThat(afterSwitch.get()).as("rest of the request after the switch (old app: userA, thread-local)")
                .isEqualTo("userA");
        assertThat(strategy.getContext().getAuthentication()).as("request thread after the request").isNull();
        setSessionContext(currentVaadinSession(), userB);
        assertThat(strategy.getContext()).as("next request of the session on this thread").isSameAs(userB);
    }

    private WrappedSession currentVaadinSession() {
        final VaadinSession vaadinSession = mock(VaadinSession.class);
        vaadinSessions.add(vaadinSession);
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
