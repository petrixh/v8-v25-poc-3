/**
 * Copyright (c) 2023 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.hawkbit.ui.push;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.IntFunction;
import java.util.stream.Collectors;

import org.eclipse.hawkbit.autoconfigure.scheduling.ExecutorAutoConfiguration;
import org.eclipse.hawkbit.im.authentication.PermissionService;
import org.eclipse.hawkbit.im.authentication.SpPermission;
import org.eclipse.hawkbit.im.authentication.TenantAwareAuthenticationDetails;
import org.eclipse.hawkbit.repository.event.remote.DistributionSetDeletedEvent;
import org.eclipse.hawkbit.repository.event.remote.TargetDeletedEvent;
import org.eclipse.hawkbit.repository.model.DistributionSet;
import org.eclipse.hawkbit.repository.model.Target;
import org.eclipse.hawkbit.security.SecurityContextTenantAware;
import org.eclipse.hawkbit.ui.SpPermissionChecker;
import org.eclipse.hawkbit.ui.autoconfigure.ThreadFirstSecurityContextHolderStrategy;
import org.eclipse.hawkbit.ui.common.event.EntityModifiedEventPayload;
import org.eclipse.hawkbit.ui.common.event.EventTopics;
import org.eclipse.hawkbit.ui.push.event.RolloutChangedEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.AdditionalAnswers;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ApplicationEvent;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.vaadin.spring.events.EventBus.UIEventBus;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.server.Command;
import com.vaadin.flow.server.VaadinSession;
import com.vaadin.flow.server.VaadinSessionState;
import com.vaadin.flow.server.WrappedSession;

import io.qameta.allure.Description;
import io.qameta.allure.Feature;
import io.qameta.allure.Story;

/**
 * Push of entity changes to the open UIs: every UI runs its own dispatch job on
 * the shared {@code scheduledExecutorService}. Two UIs of users in two tenants
 * get every event of both tenants offered; each UI must receive only its own
 * tenant's events, filtered by its own user's permissions, and run its UI
 * update as its own user, also when both jobs share one pooled thread.
 */
@Feature("Unit Tests - Management UI")
@Story("Tenant isolation of push")
class DelayedEventBusPushStrategyTenantIsolationTest {

    private static final String TENANT_A = "TENANT_A";
    private static final String TENANT_B = "TENANT_B";
    private static final String USER_A = "userA";
    private static final String USER_B = "userB";
    private static final long PUSH_DELAY_MS = 20;
    private static final int ROUNDS = 5;
    private static final long TIMEOUT_MS = 15_000;
    private static final SecurityContextTenantAware TENANT_AWARE = new SecurityContextTenantAware(null);
    // kinds of entity, the last digit of an entity id
    private static final int TARGET = 1;
    private static final int DISTRIBUTION_SET = 2;
    private static final int ROLLOUT = 3;

    // Flow keeps the current VaadinSession only weakly: hold the mocks for the
    // whole test
    private final List<Object> mocks = new ArrayList<>();
    private final Queue<Delivery> deliveries = new ConcurrentLinkedQueue<>();

    @BeforeEach
    void defaultStrategyWhileExecutorsAreBuilt() {
        SecurityContextHolder.setStrategyName(SecurityContextHolder.MODE_THREADLOCAL);
    }

    @AfterEach
    void cleanUp() {
        // the UI strategy keeps a switched context in a static thread-local: clear it on this thread
        SecurityContextHolder.clearContext();
        VaadinSession.setCurrent(null);
        SecurityContextHolder.setStrategyName(SecurityContextHolder.MODE_THREADLOCAL);
    }

    @ParameterizedTest(name = "scheduler threads: {0}")
    @ValueSource(ints = { 1, 3 })
    @Description("Two UIs of users in two tenants, events of both tenants offered to both: each UI gets only its tenant's events that its user may read, and updates as its own user")
    void eachUiGetsOnlyItsTenantsEventsItsUserMayRead(final int schedulerThreads) {
        new ApplicationContextRunner().withUserConfiguration(ExecutorAutoConfiguration.class)
                .withPropertyValues("hawkbit.threadpool.schedulerThreads=" + schedulerThreads).run(ctx -> {
                    final ScheduledExecutorService executor = startingAtOnce(
                            ctx.getBean("scheduledExecutorService", ScheduledExecutorService.class));
                    // as vaadin-spring's initializer does once the singletons (the executors) exist
                    SecurityContextHolder.setContextHolderStrategy(new ThreadFirstSecurityContextHolderStrategy());

                    // userA may read targets and rollouts, userB distribution sets and rollouts
                    final UI uiA = ui(1, context(USER_A, TENANT_A, SpPermission.READ_TARGET,
                            SpPermission.READ_ROLLOUT));
                    final UI uiB = ui(2, context(USER_B, TENANT_B, SpPermission.READ_REPOSITORY,
                            SpPermission.READ_ROLLOUT));
                    // tenant A's targets and rollouts for userA; tenant B's distribution sets and rollouts for userB
                    final Set<Long> expectedA = ids(round -> List.of(id(TENANT_A, round, TARGET),
                            id(TENANT_A, round, ROLLOUT)));
                    final Set<Long> expectedB = ids(round -> List.of(id(TENANT_B, round, DISTRIBUTION_SET),
                            id(TENANT_B, round, ROLLOUT)));
                    final DelayedEventBusPushStrategy pushA = strategy(executor);
                    final DelayedEventBusPushStrategy pushB = strategy(executor);
                    // each UI starts its push job in a request of its user, as AbstractHawkbitUI does
                    initInRequest(pushA, uiA);
                    initInRequest(pushB, uiB);
                    try {
                        for (int round = 0; round < ROUNDS; round++) {
                            // the multicaster offers every event to every UI's strategy
                            for (final ApplicationEvent event : events(round)) {
                                pushA.onApplicationEvent(event);
                                pushB.onApplicationEvent(event);
                            }
                            Thread.sleep(PUSH_DELAY_MS);
                        }

                        awaitDelivered(uiA, expectedA);
                        awaitDelivered(uiB, expectedB);
                        // a few more dispatch rounds for anything that should not come
                        Thread.sleep(5 * PUSH_DELAY_MS);
                    } finally {
                        pushA.clean();
                        pushB.clean();
                    }

                    assertThat(idsDeliveredTo(uiA)).as("entities pushed to the UI of userA (tenant A)")
                            .containsExactlyInAnyOrderElementsOf(expectedA);
                    assertThat(idsDeliveredTo(uiB)).as("entities pushed to the UI of userB (tenant B)")
                            .containsExactlyInAnyOrderElementsOf(expectedB);
                    assertThat(deliveries.stream().filter(d -> d.ui() == uiA).map(Delivery::asUser)
                            .collect(Collectors.toSet())).as("user and tenant while updating the UI of userA")
                            .containsExactly(USER_A + "@" + TENANT_A);
                    assertThat(deliveries.stream().filter(d -> d.ui() == uiB).map(Delivery::asUser)
                            .collect(Collectors.toSet())).as("user and tenant while updating the UI of userB")
                            .containsExactly(USER_B + "@" + TENANT_B);
                    if (schedulerThreads == 1) {
                        final Set<Thread> threadsA = threadsOf(uiA);
                        threadsA.retainAll(threadsOf(uiB));
                        assertThat(threadsA).as("pooled threads that dispatched to both UIs").isNotEmpty();
                    }
                });
    }

    private static long id(final String tenant, final int round, final int kind) {
        return (TENANT_A.equals(tenant) ? 100_000L : 200_000L) + round * 10L + kind;
    }

    private static Set<Long> ids(final IntFunction<List<Long>> perRound) {
        final Set<Long> ids = new HashSet<>();
        for (int round = 0; round < ROUNDS; round++) {
            ids.addAll(perRound.apply(round));
        }
        return ids;
    }

    private static List<ApplicationEvent> events(final int round) {
        final List<ApplicationEvent> events = new ArrayList<>();
        for (final String tenant : List.of(TENANT_A, TENANT_B)) {
            events.add(new TargetDeletedEvent(tenant, id(tenant, round, TARGET), "controller", null, Target.class,
                    "vv8-ui"));
            events.add(new DistributionSetDeletedEvent(tenant, id(tenant, round, DISTRIBUTION_SET),
                    DistributionSet.class, "vv8-ui"));
            events.add(new RolloutChangedEvent(tenant, id(tenant, round, ROLLOUT)));
        }
        return events;
    }

    private DelayedEventBusPushStrategy strategy(final ScheduledExecutorService executor) {
        final UIEventBus eventBus = mock(UIEventBus.class);
        mocks.add(eventBus);
        doAnswer(invocation -> {
            final Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            deliveries.add(new Delivery(invocation.getArgument(1), invocation.getArgument(2),
                    authentication == null ? null : authentication.getName(), TENANT_AWARE.getCurrentTenant(),
                    Thread.currentThread()));
            return null;
        }).when(eventBus).publish(eq(EventTopics.REMOTE_EVENT_RECEIVED), any(), any());
        final SpPermissionChecker permissionChecker = new SpPermissionChecker(new PermissionService()) {
            private static final long serialVersionUID = 1L;
        };
        return new DelayedEventBusPushStrategy(executor, eventBus, new HawkbitEventProvider(),
                new HawkbitEventPermissionChecker(permissionChecker), PUSH_DELAY_MS);
    }

    private static void initInRequest(final DelayedEventBusPushStrategy strategy, final UI ui) {
        VaadinSession.setCurrent(ui.getSession());
        try {
            strategy.init(ui);
        } finally {
            VaadinSession.setCurrent(null);
        }
    }

    /**
     * A UI whose session holds the user; {@code access} runs the command with
     * the session current, as Flow does when it gets the session lock.
     */
    private UI ui(final int id, final SecurityContext user) {
        final VaadinSession session = mock(VaadinSession.class);
        final WrappedSession httpSession = mock(WrappedSession.class);
        final UI ui = mock(UI.class);
        mocks.add(session);
        mocks.add(httpSession);
        mocks.add(ui);
        when(session.getSession()).thenReturn(httpSession);
        when(session.getState()).thenReturn(VaadinSessionState.OPEN);
        when(httpSession.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY))
                .thenReturn(user);
        when(ui.getSession()).thenReturn(session);
        when(ui.getUIId()).thenReturn(id);
        when(ui.access(any(Command.class))).thenAnswer(invocation -> {
            final VaadinSession previous = VaadinSession.getCurrent();
            VaadinSession.setCurrent(session);
            try {
                invocation.<Command> getArgument(0).execute();
            } finally {
                VaadinSession.setCurrent(previous);
            }
            return CompletableFuture.completedFuture(null);
        });
        return ui;
    }

    /**
     * The real executor, but the dispatch jobs start at once instead of after
     * 10 s.
     */
    private ScheduledExecutorService startingAtOnce(final ScheduledExecutorService real) {
        final ScheduledExecutorService executor = mock(ScheduledExecutorService.class,
                AdditionalAnswers.delegatesTo(real));
        mocks.add(executor);
        doAnswer(invocation -> real.scheduleWithFixedDelay(invocation.getArgument(0), 0,
                invocation.<Long> getArgument(2), invocation.getArgument(3))).when(executor)
                .scheduleWithFixedDelay(any(Runnable.class), anyLong(), anyLong(), any(TimeUnit.class));
        return executor;
    }

    private void awaitDelivered(final UI ui, final Set<Long> expected) throws InterruptedException {
        final long end = System.currentTimeMillis() + TIMEOUT_MS;
        while (!idsDeliveredTo(ui).containsAll(expected) && System.currentTimeMillis() < end) {
            Thread.sleep(PUSH_DELAY_MS);
        }
    }

    private List<Long> idsDeliveredTo(final UI ui) {
        return deliveries.stream().filter(d -> d.ui() == ui)
                .flatMap(d -> d.payload().getEntityIds().stream()).collect(Collectors.toList());
    }

    private Set<Thread> threadsOf(final UI ui) {
        return deliveries.stream().filter(d -> d.ui() == ui).map(Delivery::thread)
                .collect(Collectors.toCollection(HashSet::new));
    }

    private static SecurityContext context(final String name, final String tenant, final String... authorities) {
        final TestingAuthenticationToken authentication = new TestingAuthenticationToken(name, null, authorities);
        authentication.setDetails(new TenantAwareAuthenticationDetails(tenant, false));
        return new SecurityContextImpl(authentication);
    }

    record Delivery(Object ui, EntityModifiedEventPayload payload, String user, String tenant, Thread thread) {
        String asUser() {
            return user + "@" + tenant;
        }
    }
}
