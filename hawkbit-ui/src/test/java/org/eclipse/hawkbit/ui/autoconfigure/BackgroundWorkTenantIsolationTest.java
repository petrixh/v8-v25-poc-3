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
import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.hawkbit.autoconfigure.repository.event.EventPublisherAutoConfiguration;
import org.eclipse.hawkbit.autoconfigure.scheduling.AsyncConfigurerAutoConfiguration;
import org.eclipse.hawkbit.autoconfigure.scheduling.ExecutorAutoConfiguration;
import org.eclipse.hawkbit.im.authentication.TenantAwareAuthenticationDetails;
import org.eclipse.hawkbit.repository.event.remote.TargetDeletedEvent;
import org.eclipse.hawkbit.repository.model.Target;
import org.eclipse.hawkbit.security.SecurityContextTenantAware;
import org.eclipse.hawkbit.tenancy.TenantAware;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cloud.bus.ServiceMatcher;
import org.springframework.cloud.bus.event.RemoteApplicationEvent;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;

import com.vaadin.flow.server.VaadinSession;
import com.vaadin.flow.server.WrappedSession;

import io.qameta.allure.Description;
import io.qameta.allure.Feature;
import io.qameta.allure.Story;

/**
 * Background work of two users in two tenants, interleaved on the shared
 * executors of the UI process: every task, {@code @Async} call and event
 * listener runs as the user and tenant that submitted it, never as the other
 * one, and a pooled thread does not hand a context to its next task. Events
 * that arrive over the Bus from another node run as their own tenant.
 * <p>
 * The context is built as in the running UI process: the executors are created
 * with Spring's default strategy, then the UI installs
 * {@link ThreadFirstSecurityContextHolderStrategy}, and the requests' users sit
 * in their Vaadin sessions.
 */
@Feature("Unit Tests - Management UI")
@Story("Tenant isolation of background work")
class BackgroundWorkTenantIsolationTest {

    private static final String TENANT_A = "TENANT_A";
    private static final String TENANT_B = "TENANT_B";
    private static final String USER_A = "userA";
    private static final String USER_B = "userB";
    private static final String THIS_NODE = "vv8-ui";
    private static final String OTHER_NODE = "mgmt-server";
    private static final int TASKS_PER_TENANT = 30;
    private static final long TIMEOUT_SECONDS = 20;
    private static final TenantAware TENANT_AWARE = new SecurityContextTenantAware(null);

    // Flow keeps the current VaadinSession only weakly: hold the mocks for the
    // whole test, or the garbage collector may drop them mid-test
    private final List<VaadinSession> vaadinSessions = new ArrayList<>();

    enum Pools {
        /** the pool sizes of the running app */
        DEFAULT,
        /**
         * one thread in each hawkBit pool, so tasks of both tenants must reuse
         * the same thread (the UI executor has one core thread anyway)
         */
        ONE_THREAD
    }

    enum Path {
        ASYNC_EXECUTOR_SUBMIT("asyncExecutor submit") {
            @Override
            void submit(final ApplicationContext ctx, final Runnable task) {
                ctx.getBean("asyncExecutor", ExecutorService.class).submit(task);
            }
        },
        ASYNC_EXECUTOR_EXECUTE("asyncExecutor execute") {
            @Override
            void submit(final ApplicationContext ctx, final Runnable task) {
                ctx.getBean("asyncExecutor", ExecutorService.class).execute(task);
            }
        },
        TASK_EXECUTOR("taskExecutor") {
            @Override
            void submit(final ApplicationContext ctx, final Runnable task) {
                ctx.getBean("taskExecutor", TaskExecutor.class).execute(task);
            }
        },
        UI_EXECUTOR("uiExecutor (uploads)") {
            @Override
            void submit(final ApplicationContext ctx, final Runnable task) {
                ctx.getBean("uiExecutor", Executor.class).execute(task);
            }
        },
        SCHEDULED_EXECUTOR("scheduledExecutorService (push dispatch)") {
            @Override
            void submit(final ApplicationContext ctx, final Runnable task) {
                ctx.getBean("scheduledExecutorService", ScheduledExecutorService.class).schedule(task, 0,
                        TimeUnit.MILLISECONDS);
            }
        },
        TASK_SCHEDULER("taskScheduler (@Scheduled)") {
            @Override
            void submit(final ApplicationContext ctx, final Runnable task) {
                ctx.getBean("taskScheduler", TaskScheduler.class).schedule(task, Instant.now());
            }
        },
        ASYNC_METHOD("@Async method (rollout group validation)") {
            @Override
            void submit(final ApplicationContext ctx, final Runnable task) {
                ctx.getBean(AsyncProbe.class).run(task);
            }
        },
        EVENT_MULTICASTER("application event multicaster") {
            @Override
            void submit(final ApplicationContext ctx, final Runnable task) {
                ctx.publishEvent(new ProbeEvent(task));
            }
        };

        private final String label;

        Path(final String label) {
            this.label = label;
        }

        abstract void submit(ApplicationContext ctx, Runnable task);

        @Override
        public String toString() {
            return label;
        }
    }

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

    static Stream<Arguments> pathsAndPools() {
        return Stream.of(Pools.values())
                .flatMap(pools -> Stream.of(Path.values()).map(path -> Arguments.of(path, pools)));
    }

    @ParameterizedTest(name = "{0}, {1} pools")
    @MethodSource("pathsAndPools")
    @Description("Requests of two users in two tenants hand work to the same executor at the same time: every task sees its own submitter's user and tenant, at its start and later on")
    void interleavedWorkOfTwoTenantsSeesOnlyItsOwnUserAndTenant(final Path path, final Pools pools) {
        runner(pools).run(ctx -> {
            installUiStrategy();
            final VaadinSession sessionA = vaadinSessionOf(context(USER_A, TENANT_A));
            final VaadinSession sessionB = vaadinSessionOf(context(USER_B, TENANT_B));
            final Queue<Seen> seen = new ConcurrentLinkedQueue<>();
            final CountDownLatch done = new CountDownLatch(2 * TASKS_PER_TENANT);
            final CountDownLatch start = new CountDownLatch(1);
            final Queue<Throwable> errors = new ConcurrentLinkedQueue<>();

            final Thread requestsA = requestThread("request-A", sessionA, start, errors, () -> {
                for (int i = 0; i < TASKS_PER_TENANT; i++) {
                    path.submit(ctx, probe(USER_A, TENANT_A, seen, done));
                    Thread.yield();
                }
            });
            final Thread requestsB = requestThread("request-B", sessionB, start, errors, () -> {
                for (int i = 0; i < TASKS_PER_TENANT; i++) {
                    path.submit(ctx, probe(USER_B, TENANT_B, seen, done));
                    Thread.yield();
                }
            });
            start.countDown();
            requestsA.join(TimeUnit.SECONDS.toMillis(TIMEOUT_SECONDS));
            requestsB.join(TimeUnit.SECONDS.toMillis(TIMEOUT_SECONDS));
            assertThat(errors).as("errors on the request threads").isEmpty();
            assertThat(done.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)).as("all tasks finished").isTrue();

            assertThat(seen).hasSize(2 * TASKS_PER_TENANT);
            assertThat(seen).as("tasks that ran on a pooled thread, not on their submitter's")
                    .anyMatch(s -> s.thread() != requestsA && s.thread() != requestsB);
            assertThat(seen.stream().filter(s -> !s.asSubmitted()).map(Seen::toString).collect(Collectors.toList()))
                    .as("tasks that saw another user or tenant than their submitter's").isEmpty();
            // the UI executor has a fixed size (1 core thread, 20 queued, then up to 20 threads): its reuse
            // across tenants is shown on one thread by pooledThreadDoesNotHandALeftContextToTheNextTask
            if (pools == Pools.ONE_THREAD && path != Path.UI_EXECUTOR) {
                assertThat(threadsThatRanBothTenants(seen, Set.of(requestsA, requestsB)))
                        .as("pooled threads that ran tasks of both tenants").isNotEmpty();
            }
        });
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(Path.class)
    @Description("A task that leaves another context on its pooled thread (does not restore it) does not hand it to the next task on that thread: a task of another tenant's user sees that user, a task with no submitter sees no user and no tenant")
    void pooledThreadDoesNotHandALeftContextToTheNextTask(final Path path) {
        runner(Pools.ONE_THREAD).run(ctx -> {
            installUiStrategy();
            final VaadinSession sessionA = vaadinSessionOf(context(USER_A, TENANT_A));
            final VaadinSession sessionB = vaadinSessionOf(context(USER_B, TENANT_B));

            VaadinSession.setCurrent(sessionA);
            final Seen leaving = runAndSee(ctx, path, null,
                    () -> SecurityContextHolder.setContext(context("leftByTaskOfA", TENANT_A)));
            final Thread worker = leaving.thread();

            VaadinSession.setCurrent(sessionB);
            final Seen ofB = runAndSee(ctx, path, worker, null);

            VaadinSession.setCurrent(null);
            SecurityContextHolder.clearContext();
            final Seen ofNobody = runAndSee(ctx, path, worker, null);

            // all checked together, so a failure shows what each later task saw
            assertSoftly(softly -> {
                softly.assertThat(leaving.user()).as("user of the task that leaves a context").isEqualTo(USER_A);
                softly.assertThat(ofB.user()).as("next task, from a request of a user in another tenant: user")
                        .isEqualTo(USER_B);
                softly.assertThat(ofB.tenant()).as("next task, from a request of a user in another tenant: tenant")
                        .isEqualTo(TENANT_B);
                softly.assertThat(ofNobody.user()).as("next task, from a submitter with no user: user").isNull();
                softly.assertThat(ofNobody.tenant()).as("next task, from a submitter with no user: tenant").isNull();
            });
        });
    }

    @ParameterizedTest(name = "{0} pools")
    @EnumSource(Pools.class)
    @Description("Entity events of both tenants arrive over the Bus from another node while UI requests of both tenants publish their own: each listener call runs as the event's tenant; local events also as the publishing user, Bus events as no request user")
    void busEventsRunAsTheirTenantInterleavedWithLocalEvents(final Pools pools) {
        runner(pools).run(ctx -> {
            installUiStrategy();
            final RecordingListener listener = ctx.getBean(RecordingListener.class);
            final VaadinSession sessionA = vaadinSessionOf(context(USER_A, TENANT_A));
            final VaadinSession sessionB = vaadinSessionOf(context(USER_B, TENANT_B));
            final Queue<Seen> seen = new ConcurrentLinkedQueue<>();
            final CountDownLatch done = new CountDownLatch(4 * TASKS_PER_TENANT);
            final CountDownLatch start = new CountDownLatch(1);
            final Queue<Throwable> errors = new ConcurrentLinkedQueue<>();

            final List<Thread> threads = List.of(
                    // a UI request changes an entity: the event is published on the request thread
                    requestThread("request-A", sessionA, start, errors,
                            () -> publish(ctx, listener, TENANT_A, THIS_NODE, 1000, USER_A, seen, done)),
                    requestThread("request-B", sessionB, start, errors,
                            () -> publish(ctx, listener, TENANT_B, THIS_NODE, 2000, USER_B, seen, done)),
                    // the Bus consumer of this node delivers events of the other node, with no user
                    requestThread("bus-consumer-1", null, start, errors,
                            () -> publish(ctx, listener, TENANT_A, OTHER_NODE, 3000, null, seen, done)),
                    requestThread("bus-consumer-2", null, start, errors,
                            () -> publish(ctx, listener, TENANT_B, OTHER_NODE, 4000, null, seen, done)));
            start.countDown();
            for (final Thread thread : threads) {
                thread.join(TimeUnit.SECONDS.toMillis(TIMEOUT_SECONDS));
            }
            assertThat(errors).as("errors on the publishing threads").isEmpty();
            assertThat(done.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)).as("all events delivered").isTrue();

            assertThat(seen).hasSize(4 * TASKS_PER_TENANT);
            assertThat(seen.stream().filter(s -> !s.asSubmitted()).map(Seen::toString).collect(Collectors.toList()))
                    .as("listener calls that saw another user or tenant than the event's").isEmpty();
            if (pools == Pools.ONE_THREAD) {
                assertThat(threadsThatRanBothTenants(seen, Set.copyOf(threads)))
                        .as("pooled threads that ran listeners of both tenants").isNotEmpty();
            }
        });
    }

    private static void publish(final ApplicationContext ctx, final RecordingListener listener, final String tenant,
            final String node, final long firstId, final String expectedUser, final Queue<Seen> seen,
            final CountDownLatch done) {
        for (int i = 0; i < TASKS_PER_TENANT; i++) {
            final long id = firstId + i;
            final TargetDeletedEvent event = new TargetDeletedEvent(tenant, id, "controller-" + id, null, Target.class,
                    node);
            listener.onEvent(event, probe(expectedUser, tenant, seen, done));
            ctx.publishEvent(event);
            Thread.yield();
        }
    }

    private ApplicationContextRunner runner(final Pools pools) {
        final ApplicationContextRunner runner = new ApplicationContextRunner()
                .withPropertyValues("spring.cloud.bus.enabled=false")
                .withUserConfiguration(Probes.class, HawkbitAutoConfigurations.class);
        return pools == Pools.ONE_THREAD
                ? runner.withPropertyValues("hawkbit.threadpool.corethreads=1", "hawkbit.threadpool.maxthreads=1",
                        "hawkbit.threadpool.schedulerThreads=1")
                : runner;
    }

    /**
     * Runs one task on the path and returns what it saw; with an expected
     * worker, the task must run on that pooled thread.
     */
    private static Seen runAndSee(final ApplicationContext ctx, final Path path, final Thread expectedWorker,
            final Runnable afterSeeing) throws InterruptedException {
        final Queue<Seen> seen = new ConcurrentLinkedQueue<>();
        final CountDownLatch done = new CountDownLatch(1);
        final Runnable probe = probe(null, null, seen, done);
        path.submit(ctx, () -> {
            try {
                probe.run();
            } finally {
                if (afterSeeing != null) {
                    afterSeeing.run();
                }
            }
        });
        assertThat(done.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)).as("task finished").isTrue();
        final Seen result = seen.peek();
        assertThat(result.thread()).as("task ran on a pooled thread").isNotSameAs(Thread.currentThread());
        if (expectedWorker != null) {
            assertThat(result.thread()).as("task ran on the same pooled thread").isSameAs(expectedWorker);
        }
        return result;
    }

    private Thread requestThread(final String name, final VaadinSession session, final CountDownLatch start,
            final Queue<Throwable> errors, final ThrowingRunnable work) {
        final Thread thread = new Thread(() -> {
            // the request's user sits in its Vaadin session, as in a UI request
            VaadinSession.setCurrent(session);
            try {
                start.await();
                work.run();
            } catch (final Throwable e) {
                errors.add(e);
            } finally {
                VaadinSession.setCurrent(null);
                SecurityContextHolder.clearContext();
            }
        }, name);
        thread.start();
        return thread;
    }

    /**
     * A task that records the user and tenant it sees at its start and again
     * a moment later, while other tasks run.
     */
    private static Runnable probe(final String expectedUser, final String expectedTenant, final Queue<Seen> seen,
            final CountDownLatch done) {
        return () -> {
            try {
                final String user = currentUser();
                final String tenant = TENANT_AWARE.getCurrentTenant();
                pause();
                seen.add(new Seen(expectedUser, expectedTenant, user, tenant, currentUser(),
                        TENANT_AWARE.getCurrentTenant(), Thread.currentThread()));
            } finally {
                done.countDown();
            }
        };
    }

    private static void pause() {
        try {
            Thread.sleep(1);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static String currentUser() {
        final Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication == null ? null : authentication.getName();
    }

    private static Set<Thread> threadsThatRanBothTenants(final Queue<Seen> seen, final Set<Thread> submitters) {
        final Map<Thread, Set<String>> tenantsByThread = seen.stream()
                .filter(s -> !submitters.contains(s.thread()))
                .collect(Collectors.groupingBy(Seen::thread,
                        Collectors.mapping(Seen::expectedTenant, Collectors.toSet())));
        return tenantsByThread.entrySet().stream().filter(e -> e.getValue().size() > 1).map(Map.Entry::getKey)
                .collect(Collectors.toSet());
    }

    private static void installUiStrategy() {
        // as vaadin-spring's initializer does once the singletons (the executors) exist
        SecurityContextHolder.setContextHolderStrategy(new ThreadFirstSecurityContextHolderStrategy());
    }

    private VaadinSession vaadinSessionOf(final SecurityContext user) {
        final VaadinSession vaadinSession = mock(VaadinSession.class);
        vaadinSessions.add(vaadinSession);
        final WrappedSession httpSession = mock(WrappedSession.class);
        when(vaadinSession.getSession()).thenReturn(httpSession);
        when(httpSession.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY))
                .thenReturn(user);
        return vaadinSession;
    }

    private static SecurityContext context(final String name, final String tenant) {
        final TestingAuthenticationToken authentication = new TestingAuthenticationToken(name, null);
        authentication.setDetails(new TenantAwareAuthenticationDetails(tenant, false));
        return new SecurityContextImpl(authentication);
    }

    /**
     * What a task saw. Bus events from another node expect no user (they run
     * as the tenant's system context).
     */
    record Seen(String expectedUser, String expectedTenant, String user, String tenant, String userLater,
            String tenantLater, Thread thread) {

        boolean asSubmitted() {
            return Objects.equals(expectedUser, user) && Objects.equals(expectedTenant, tenant)
                    && Objects.equals(expectedUser, userLater) && Objects.equals(expectedTenant, tenantLater);
        }
    }

    @FunctionalInterface
    interface ThrowingRunnable {
        void run() throws Exception;
    }

    static final class ProbeEvent extends ApplicationEvent {
        private static final long serialVersionUID = 1L;

        private final transient Runnable probe;

        ProbeEvent(final Runnable probe) {
            super(probe);
            this.probe = probe;
        }
    }

    /**
     * Listener on the async multicaster: runs the probe of a {@link ProbeEvent},
     * or the probe registered for a hawkBit entity event.
     */
    static class RecordingListener implements ApplicationListener<ApplicationEvent> {

        private final Map<ApplicationEvent, Runnable> probes = Collections.synchronizedMap(new IdentityHashMap<>());

        void onEvent(final ApplicationEvent event, final Runnable probe) {
            probes.put(event, probe);
        }

        @Override
        public void onApplicationEvent(final ApplicationEvent event) {
            if (event instanceof ProbeEvent probeEvent) {
                probeEvent.probe.run();
                return;
            }
            final Runnable probe = probes.remove(event);
            if (probe != null) {
                probe.run();
            }
        }
    }

    static class AsyncProbe {
        @Async
        public CompletableFuture<Void> run(final Runnable probe) {
            probe.run();
            return CompletableFuture.completedFuture(null);
        }
    }

    /**
     * Imported as the app imports them, so that conditions are evaluated before
     * the configuration is registered: AsyncConfigurerAutoConfiguration is
     * conditional on no other AsyncConfigurer and would find itself when
     * registered directly (as {@code AutoConfigurations.of} in a context
     * runner does), leaving {@code @Async} off.
     */
    @Configuration(proxyBeanMethods = false)
    @ImportAutoConfiguration({ ExecutorAutoConfiguration.class, AsyncConfigurerAutoConfiguration.class,
            EventPublisherAutoConfiguration.class })
    static class HawkbitAutoConfigurations {
    }

    @Configuration(proxyBeanMethods = false)
    static class Probes {

        @Bean
        TenantAware tenantAware() {
            return TENANT_AWARE;
        }

        @Bean
        ServiceMatcher serviceMatcher() {
            final ServiceMatcher serviceMatcher = mock(ServiceMatcher.class);
            when(serviceMatcher.isFromSelf(any())).thenAnswer(invocation -> !OTHER_NODE
                    .equals(invocation.<RemoteApplicationEvent> getArgument(0).getOriginService()));
            return serviceMatcher;
        }

        @Bean
        RecordingListener recordingListener() {
            return new RecordingListener();
        }

        @Bean
        AsyncProbe asyncProbe() {
            return new AsyncProbe();
        }
    }
}
