/**
 * Copyright (c) 2015 Bosch Software Innovations GmbH and others
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.hawkbit.autoconfigure.scheduling;

import java.util.concurrent.Executor;

import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.aop.interceptor.SimpleAsyncUncaughtExceptionHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.task.TaskExecutionAutoConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 *
 * Auto config fot the exception handler.
 */
@Configuration
@EnableAsync
@ConditionalOnMissingBean(AsyncConfigurer.class)
// Spring Boot 4 registers its own AsyncConfigurer in TaskExecutionAutoConfiguration, which the auto-configuration
// order evaluates first: this one would then not apply (no @EnableAsync, no asyncExecutor). Boot 2.7 had none.
@AutoConfigureBefore(TaskExecutionAutoConfiguration.class)
public class AsyncConfigurerAutoConfiguration implements AsyncConfigurer {

    @Autowired
    @Qualifier("asyncExecutor")
    private Executor executor;

    @Override
    public Executor getAsyncExecutor() {
        return executor;
    }

    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return new SimpleAsyncUncaughtExceptionHandler();
    }

}
