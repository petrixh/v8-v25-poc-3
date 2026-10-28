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

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import org.eclipse.hawkbit.autoconfigure.scheduling.ExecutorAutoConfiguration;
import org.eclipse.hawkbit.im.authentication.TenantAwareAuthenticationDetails;
import org.eclipse.hawkbit.security.SecurityContextTenantAware;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;

import com.vaadin.flow.server.VaadinSession;
import com.vaadin.flow.server.WrappedSession;

import io.qameta.allure.Description;
import io.qameta.allure.Feature;
import io.qameta.allure.Story;

/**
 * The executors of {@link ExecutorAutoConfiguration} are built before the UI
 * installs its security context holder strategy (as in the running app). Work
 * handed to them must still run with the submitting thread's user and tenant,
 * and leave nothing on the worker thread afterwards.
 */
@Feature("Unit Tests - Management UI")
@Story("Security context in executor tasks")
class ExecutorSecurityContextPropagationTest {

    private static final String TENANT = "DEFAULT";
    private static final long TIMEOUT_SECONDS = 10;

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(ExecutorAutoConfiguration.class);

    private RecordingStrategy installed;

    @BeforeEach
    void defaultStrategyWhileExecutorsAreBuilt() {
        SecurityContextHolder.setStrategyName(SecurityContextHolder.MODE_THREADLOCAL);
    }

    @AfterEach
    void cleanUp() {
        if (installed != null) {
            installed.clearContext();
        }
        VaadinSession.setCurrent(null);
        SecurityContextHolder.setStrategyName(SecurityContextHolder.MODE_THREADLOCAL);
    }

    static Stream<Arguments> executors() {
        return Stream.of(
                Arguments.of("asyncExecutor submit (Callable)", (Submitter) (ctx, task) -> await(
                        ctx.getBean("asyncExecutor", ExecutorService.class).submit(() -> {
                            task.run();
                            return null;
                        }))),
                Arguments.of("asyncExecutor execute (@Async, event multicaster)",
                        (Submitter) (ctx, task) -> runAndWait(ctx.getBean("asyncExecutor", ExecutorService.class),
                                task)),
                Arguments.of("taskExecutor execute",
                        (Submitter) (ctx, task) -> runAndWait(ctx.getBean("taskExecutor", TaskExecutor.class), task)),
                Arguments.of("uiExecutor execute",
                        (Submitter) (ctx, task) -> runAndWait(ctx.getBean("uiExecutor", Executor.class), task)),
                Arguments.of("scheduledExecutorService schedule", (Submitter) (ctx, task) -> await(ctx
                        .getBean("scheduledExecutorService", ScheduledExecutorService.class).schedule(() -> {
                            task.run();
                            return null;
                        }, 10, TimeUnit.MILLISECONDS))),
                Arguments.of("taskScheduler schedule", (Submitter) (ctx, task) -> runAndWait(
                        command -> ctx.getBean("taskScheduler", TaskScheduler.class).schedule(command, Instant.now()),
                        task)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("executors")
    @Description("A task submitted from a UI request sees the session's user and tenant, and the worker thread is clean afterwards")
    void taskSeesSessionUserAndTenant(final String name, final Submitter submitter) {
        contextRunner.run(ctx -> {
            installUiStrategy();
            final WrappedSession httpSession = currentVaadinSession();
            final SecurityContext user = context("readonly");
            when(httpSession.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY))
                    .thenReturn(user);

            assertTaskSeesAndCleansUp(ctx, submitter, "readonly");
            assertThat(SecurityContextHolder.getContext()).isSameAs(user);
        });
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("executors")
    @Description("A task submitted inside a switched context (runAsSystem) sees that context, as with the old thread-local holder")
    void taskSeesSwitchedContext(final String name, final Submitter submitter) {
        contextRunner.run(ctx -> {
            installUiStrategy();
            final WrappedSession httpSession = currentVaadinSession();
            final SecurityContext user = context("readonly");
            when(httpSession.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY))
                    .thenReturn(user);

            final SecurityContext old = SecurityContextHolder.getContext();
            SecurityContextHolder.setContext(context(SecurityContextTenantAware.SYSTEM_USER));
            try {
                assertTaskSeesAndCleansUp(ctx, submitter, SecurityContextTenantAware.SYSTEM_USER);
            } finally {
                SecurityContextHolder.setContext(old);
            }
            assertThat(SecurityContextHolder.getContext()).isSameAs(user);
        });
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("executors")
    @Description("After a user's task, a task submitted with no user runs with no user")
    void taskOfAnonymousSubmitterSeesNoUser(final String name, final Submitter submitter) {
        contextRunner.run(ctx -> {
            installUiStrategy();
            final SecurityContext user = context("readonly");
            SecurityContextHolder.setContext(user);
            assertTaskSeesAndCleansUp(ctx, submitter, "readonly");
            SecurityContextHolder.clearContext();

            final AtomicReference<Optional<Authentication>> seen = new AtomicReference<>();
            submitter.submit(ctx, () -> seen.set(Optional.ofNullable(SecurityContextHolder.getContext().getAuthentication())));
            assertThat(seen.get()).isEmpty();
        });
    }

    private void assertTaskSeesAndCleansUp(final ApplicationContext ctx, final Submitter submitter,
            final String expectedUser) throws Exception {
        final AtomicReference<String> seenUser = new AtomicReference<>();
        final AtomicReference<String> seenTenant = new AtomicReference<>();
        final AtomicReference<Thread> worker = new AtomicReference<>();
        final SecurityContextTenantAware tenantAware = new SecurityContextTenantAware(null);

        submitter.submit(ctx, () -> {
            worker.set(Thread.currentThread());
            final Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            seenUser.set(authentication == null ? null : authentication.getName());
            seenTenant.set(tenantAware.getCurrentTenant());
        });

        assertThat(worker.get()).as("task ran on a worker thread").isNotSameAs(Thread.currentThread());
        assertThat(seenUser.get()).as("user inside the task").isEqualTo(expectedUser);
        assertThat(seenTenant.get()).as("tenant inside the task").isEqualTo(TENANT);
        assertThat(installed.awaitCleared(worker.get())).as("worker thread's context cleared after the task")
                .isTrue();
    }

    private void installUiStrategy() {
        // as vaadin-spring's initializer does once the singletons (the executors) exist
        installed = new RecordingStrategy(new ThreadFirstSecurityContextHolderStrategy());
        SecurityContextHolder.setContextHolderStrategy(installed);
    }

    private static WrappedSession currentVaadinSession() {
        final VaadinSession vaadinSession = mock(VaadinSession.class);
        final WrappedSession httpSession = mock(WrappedSession.class);
        when(vaadinSession.getSession()).thenReturn(httpSession);
        VaadinSession.setCurrent(vaadinSession);
        return httpSession;
    }

    private static SecurityContext context(final String name) {
        final TestingAuthenticationToken authentication = new TestingAuthenticationToken(name, null);
        authentication.setDetails(new TenantAwareAuthenticationDetails(TENANT, false));
        return new SecurityContextImpl(authentication);
    }

    private static void await(final Future<?> future) throws Exception {
        future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    private static void runAndWait(final Executor executor, final Runnable task) throws Exception {
        final CountDownLatch done = new CountDownLatch(1);
        executor.execute(() -> {
            try {
                task.run();
            } finally {
                done.countDown();
            }
        });
        assertThat(done.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)).as("task finished").isTrue();
    }

    @FunctionalInterface
    interface Submitter {
        void submit(ApplicationContext ctx, Runnable task) throws Exception;
    }

    /**
     * The installed strategy, recording per thread what the last set or clear
     * left behind, so the test can see a worker thread's state after the
     * executor's wrapper has finished.
     */
    private static final class RecordingStrategy implements SecurityContextHolderStrategy {

        private static final Optional<Authentication> NONE = Optional.empty();

        private final SecurityContextHolderStrategy delegate;
        private final Map<Thread, Optional<Authentication>> leftOnThread = new ConcurrentHashMap<>();

        private RecordingStrategy(final SecurityContextHolderStrategy delegate) {
            this.delegate = delegate;
        }

        @Override
        public void clearContext() {
            delegate.clearContext();
            record();
        }

        @Override
        public SecurityContext getContext() {
            return delegate.getContext();
        }

        @Override
        public void setContext(final SecurityContext context) {
            delegate.setContext(context);
            record();
        }

        @Override
        public SecurityContext createEmptyContext() {
            return delegate.createEmptyContext();
        }

        private void record() {
            leftOnThread.put(Thread.currentThread(),
                    Optional.ofNullable(delegate.getContext().getAuthentication()));
        }

        private boolean awaitCleared(final Thread thread) throws InterruptedException {
            final long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(TIMEOUT_SECONDS);
            while (System.nanoTime() < end) {
                if (NONE.equals(leftOnThread.get(thread))) {
                    return true;
                }
                Thread.sleep(10);
            }
            return false;
        }
    }
}
