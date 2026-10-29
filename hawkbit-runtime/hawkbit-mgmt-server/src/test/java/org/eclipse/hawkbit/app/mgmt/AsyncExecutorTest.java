/**
 * Copyright (c) 2023 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.hawkbit.app.mgmt;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.hawkbit.repository.test.util.SharedSqlTestDatabaseExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.Async;

import io.qameta.allure.Description;
import io.qameta.allure.Feature;
import io.qameta.allure.Story;

/**
 * {@code @Async} methods (e.g. RolloutManagement#validateTargetsInGroups) run
 * on hawkBit's asyncExecutor (threads "central-executor-pool-N"), not on Spring
 * Boot's default task executor and not on the calling thread.
 */
@SpringBootTest(properties = { "hawkbit.dmf.rabbitmq.enabled=false" })
@ExtendWith(SharedSqlTestDatabaseExtension.class)
@Feature("Integration Test - Executors")
@Story("Async executor")
class AsyncExecutorTest {

    @Autowired
    private AsyncProbe asyncProbe;

    @Test
    @Description("An @Async method runs on a thread of hawkBit's asyncExecutor")
    void asyncMethodRunsOnHawkbitAsyncExecutor() throws Exception {
        final String caller = Thread.currentThread().getName();

        final String worker = asyncProbe.threadName().get(10, TimeUnit.SECONDS);

        assertThat(worker).startsWith("central-executor-pool-").isNotEqualTo(caller);
    }

    static class AsyncProbe {

        @Async
        public CompletableFuture<String> threadName() {
            return CompletableFuture.completedFuture(Thread.currentThread().getName());
        }
    }

    @TestConfiguration
    static class AsyncProbeConfiguration {

        @Bean
        AsyncProbe asyncProbe() {
            return new AsyncProbe();
        }
    }
}
