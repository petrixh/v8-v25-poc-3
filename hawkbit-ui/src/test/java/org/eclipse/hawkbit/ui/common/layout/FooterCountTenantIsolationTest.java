/**
 * Copyright (c) 2023 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.hawkbit.ui.common.layout;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.eclipse.hawkbit.im.authentication.TenantAwareAuthenticationDetails;
import org.eclipse.hawkbit.security.SecurityContextTenantAware;
import org.eclipse.hawkbit.ui.autoconfigure.ThreadFirstSecurityContextHolderStrategy;
import org.eclipse.hawkbit.ui.utils.UINotification;
import org.eclipse.hawkbit.ui.utils.VaadinMessageSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.server.Command;
import com.vaadin.flow.server.VaadinSession;
import com.vaadin.flow.server.WrappedSession;

import io.qameta.allure.Description;
import io.qameta.allure.Feature;
import io.qameta.allure.Story;

/**
 * The target count in the footers runs on a thread of its own (one executor
 * per footer, not one of hawkBit's executors). Footers of users in two tenants
 * counting at the same time: every count query runs as its own footer's user
 * and tenant.
 */
@Feature("Unit Tests - Management UI")
@Story("Tenant isolation of background work")
class FooterCountTenantIsolationTest {

    private static final String TENANT_A = "TENANT_A";
    private static final String TENANT_B = "TENANT_B";
    private static final String USER_A = "userA";
    private static final String USER_B = "userB";
    private static final int COUNTS_PER_USER = 20;
    private static final long TIMEOUT_SECONDS = 20;
    private static final SecurityContextTenantAware TENANT_AWARE = new SecurityContextTenantAware(null);

    // Flow keeps the current UI and VaadinSession only weakly: hold the mocks
    private final List<Object> mocks = new ArrayList<>();
    private final Queue<Seen> seen = new ConcurrentLinkedQueue<>();

    @BeforeEach
    void installUiStrategy() {
        SecurityContextHolder.setContextHolderStrategy(new ThreadFirstSecurityContextHolderStrategy());
    }

    @AfterEach
    void cleanUp() {
        SecurityContextHolder.clearContext();
        SecurityContextHolder.setStrategyName(SecurityContextHolder.MODE_THREADLOCAL);
    }

    @Test
    @Description("Footers of two users in two tenants count at the same time: every count runs as its footer's user and tenant, each footer on its own thread")
    void countsOfTwoTenantsRunAsTheirFootersUser() throws InterruptedException {
        final CountDownLatch start = new CountDownLatch(1);
        final Queue<Throwable> errors = new ConcurrentLinkedQueue<>();
        final Thread requestsA = requestThread("request-A", USER_A, TENANT_A, start, errors);
        final Thread requestsB = requestThread("request-B", USER_B, TENANT_B, start, errors);
        start.countDown();
        requestsA.join(TimeUnit.SECONDS.toMillis(TIMEOUT_SECONDS));
        requestsB.join(TimeUnit.SECONDS.toMillis(TIMEOUT_SECONDS));

        assertThat(errors).as("errors on the request threads").isEmpty();
        assertThat(seen).hasSize(2 * COUNTS_PER_USER);
        assertThat(seen.stream().filter(s -> !s.asSubmitted()).map(Seen::toString).collect(Collectors.toList()))
                .as("counts that ran as another user or tenant than their footer's").isEmpty();
        final Set<Thread> threadsA = threadsOf(USER_A);
        assertThat(threadsA).as("count threads of userA's footer").hasSize(1)
                .doesNotContainAnyElementsOf(threadsOf(USER_B)).doesNotContain(requestsA, requestsB);
    }

    private Thread requestThread(final String name, final String expectedUser, final String expectedTenant,
            final CountDownLatch start, final Queue<Throwable> errors) {
        final VaadinSession session = vaadinSessionOf(context(expectedUser, expectedTenant));
        final UI ui = uiOf(session);
        final Thread thread = new Thread(() -> {
            // a request of the user: its Vaadin session and UI are current, the footer is built in it
            VaadinSession.setCurrent(session);
            UI.setCurrent(ui);
            try {
                final CountFooter footer = new CountFooter();
                mocks.add(footer);
                start.await();
                for (int i = 0; i < COUNTS_PER_USER; i++) {
                    final CountDownLatch counted = new CountDownLatch(1);
                    footer.count(() -> seen.add(new Seen(expectedUser, expectedTenant, currentUser(),
                            TENANT_AWARE.getCurrentTenant(), Thread.currentThread())), counted::countDown);
                    assertThat(counted.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)).as("count finished").isTrue();
                }
            } catch (final Throwable e) {
                errors.add(e);
            } finally {
                UI.setCurrent(null);
                VaadinSession.setCurrent(null);
                SecurityContextHolder.clearContext();
            }
        }, name);
        thread.start();
        return thread;
    }

    private Set<Thread> threadsOf(final String user) {
        return seen.stream().filter(s -> user.equals(s.expectedUser())).map(Seen::thread).collect(Collectors.toSet());
    }

    private static String currentUser() {
        final Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication == null ? null : authentication.getName();
    }

    private VaadinSession vaadinSessionOf(final SecurityContext user) {
        final VaadinSession session = mock(VaadinSession.class);
        final WrappedSession httpSession = mock(WrappedSession.class);
        mocks.add(session);
        mocks.add(httpSession);
        when(session.getSession()).thenReturn(httpSession);
        when(httpSession.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY))
                .thenReturn(user);
        return session;
    }

    private UI uiOf(final VaadinSession session) {
        final UI ui = mock(UI.class);
        mocks.add(ui);
        when(ui.getSession()).thenReturn(session);
        when(ui.access(any(Command.class))).thenAnswer(invocation -> {
            invocation.<Command> getArgument(0).execute();
            return CompletableFuture.completedFuture(null);
        });
        return ui;
    }

    private static SecurityContext context(final String name, final String tenant) {
        final TestingAuthenticationToken authentication = new TestingAuthenticationToken(name, null);
        authentication.setDetails(new TenantAwareAuthenticationDetails(tenant, false));
        return new SecurityContextImpl(authentication);
    }

    record Seen(String expectedUser, String expectedTenant, String user, String tenant, Thread thread) {
        boolean asSubmitted() {
            return Objects.equals(expectedUser, user) && Objects.equals(expectedTenant, tenant);
        }
    }

    private static final class CountFooter extends AbstractFooterSupport {
        private CountFooter() {
            super(mock(VaadinMessageSource.class), mock(UINotification.class));
        }

        private void count(final Runnable countValueUpdater, final Runnable countUiUpdater) {
            updateCountAsynchronously(countValueUpdater, countUiUpdater);
        }
    }
}
