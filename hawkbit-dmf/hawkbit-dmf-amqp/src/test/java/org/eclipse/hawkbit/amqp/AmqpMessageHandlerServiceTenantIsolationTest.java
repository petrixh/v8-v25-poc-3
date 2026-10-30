/**
 * Copyright (c) 2023 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.hawkbit.amqp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.hawkbit.tenancy.configuration.TenantConfigurationProperties.TenantConfigurationKey.MULTI_ASSIGNMENTS_ENABLED;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.eclipse.hawkbit.dmf.amqp.api.MessageHeaderKey;
import org.eclipse.hawkbit.dmf.amqp.api.MessageType;
import org.eclipse.hawkbit.repository.ConfirmationManagement;
import org.eclipse.hawkbit.repository.ControllerManagement;
import org.eclipse.hawkbit.repository.EntityFactory;
import org.eclipse.hawkbit.repository.TenantConfigurationManagement;
import org.eclipse.hawkbit.repository.model.Target;
import org.eclipse.hawkbit.repository.model.TenantConfigurationValue;
import org.eclipse.hawkbit.security.SecurityContextTenantAware;
import org.eclipse.hawkbit.security.SystemSecurityContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.security.core.context.SecurityContextHolder;

import io.qameta.allure.Description;
import io.qameta.allure.Feature;
import io.qameta.allure.Story;

/**
 * Device messages of two tenants, interleaved on the same listener threads:
 * each message is handled in the tenant of its header, and the listener thread
 * is left with no tenant after each message.
 */
@Feature("Component Tests - Device Management Federation API")
@Story("Tenant isolation of device messages")
class AmqpMessageHandlerServiceTenantIsolationTest {

    private static final String TENANT_A = "TENANT_A";
    private static final String TENANT_B = "TENANT_B";
    private static final int MESSAGES_PER_CONSUMER = 40;
    private static final long TIMEOUT_SECONDS = 20;

    private final SecurityContextTenantAware tenantAware = new SecurityContextTenantAware(null);
    private final MessageConverter messageConverter = new Jackson2JsonMessageConverter("*");
    private final Queue<Handled> handled = new ConcurrentLinkedQueue<>();
    private final Queue<String> leftOnThread = new ConcurrentLinkedQueue<>();

    private AmqpMessageHandlerService handler;

    @BeforeEach
    @SuppressWarnings({ "unchecked", "rawtypes" })
    void setUp() {
        SecurityContextHolder.setStrategyName(SecurityContextHolder.MODE_THREADLOCAL);
        final RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
        when(rabbitTemplate.getMessageConverter()).thenReturn(messageConverter);
        final TenantConfigurationManagement tenantConfigurationManagement = mock(TenantConfigurationManagement.class);
        final TenantConfigurationValue multiAssignments = TenantConfigurationValue.builder().value(Boolean.FALSE)
                .global(Boolean.FALSE).build();
        when(tenantConfigurationManagement.getConfigurationValue(MULTI_ASSIGNMENTS_ENABLED, Boolean.class))
                .thenReturn(multiAssignments);

        final ControllerManagement controllerManagement = mock(ControllerManagement.class);
        when(controllerManagement.findOrRegisterTargetIfItDoesNotExist(anyString(), any(URI.class)))
                .thenAnswer(invocation -> {
                    final String thingId = invocation.getArgument(0);
                    handled.add(new Handled(thingId, tenantAware.getCurrentTenant(), Thread.currentThread()));
                    // let the other consumer run in between
                    Thread.sleep(1);
                    final Target target = mock(Target.class);
                    when(target.getControllerId()).thenReturn(thingId);
                    return target;
                });
        when(controllerManagement.findActiveActionWithHighestWeight(anyString())).thenReturn(Optional.empty());

        handler = new AmqpMessageHandlerService(rabbitTemplate, mock(AmqpMessageDispatcherService.class),
                controllerManagement, mock(EntityFactory.class), new SystemSecurityContext(tenantAware),
                tenantConfigurationManagement, mock(ConfirmationManagement.class));
    }

    @AfterEach
    void cleanUp() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @Description("Two listener threads each get THING_CREATED messages of tenant A and tenant B in turn: every target is looked up in its message's tenant, and no tenant is left on a listener thread after a message")
    void messagesOfTwoTenantsOnTheSameListenerThreads() throws InterruptedException {
        final CountDownLatch start = new CountDownLatch(1);
        final Queue<Throwable> errors = new ConcurrentLinkedQueue<>();
        final List<Thread> consumers = List.of(consumer("consumer-1", start, errors),
                consumer("consumer-2", start, errors));
        start.countDown();
        for (final Thread consumer : consumers) {
            consumer.join(TimeUnit.SECONDS.toMillis(TIMEOUT_SECONDS));
        }

        assertThat(errors).as("errors on the listener threads").isEmpty();
        assertThat(handled).hasSize(2 * MESSAGES_PER_CONSUMER);
        assertThat(handled.stream().filter(h -> !h.inItsTenant()).map(Handled::toString).collect(Collectors.toList()))
                .as("messages handled in another tenant than their header's").isEmpty();
        assertThat(leftOnThread).as("tenants left on a listener thread after a message").isEmpty();
        assertThat(handled.stream().filter(h -> h.thread() == consumers.get(0)).map(Handled::tenant)
                .collect(Collectors.toSet())).as("tenants handled on one listener thread")
                .containsExactlyInAnyOrder(TENANT_A, TENANT_B);
    }

    private Thread consumer(final String name, final CountDownLatch start, final Queue<Throwable> errors) {
        final Thread thread = new Thread(() -> {
            try {
                start.await();
                for (int i = 0; i < MESSAGES_PER_CONSUMER; i++) {
                    final String tenant = i % 2 == 0 ? TENANT_A : TENANT_B;
                    handler.onMessage(thingCreated(tenant + "-" + name + "-" + i, tenant),
                            MessageType.THING_CREATED.name(), tenant, "vHost");
                    final String left = tenantAware.getCurrentTenant();
                    if (left != null || SecurityContextHolder.getContext().getAuthentication() != null) {
                        leftOnThread.add(name + " after message " + i + ": " + left);
                    }
                }
            } catch (final Throwable e) {
                errors.add(e);
            }
        }, name);
        thread.start();
        return thread;
    }

    private Message thingCreated(final String thingId, final String tenant) {
        final MessageProperties properties = new MessageProperties();
        properties.setHeader(MessageHeaderKey.TYPE, MessageType.THING_CREATED.name());
        properties.setHeader(MessageHeaderKey.TENANT, tenant);
        properties.setHeader(MessageHeaderKey.THING_ID, thingId);
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        properties.setReplyTo("replyTo");
        return messageConverter.toMessage(new byte[0], properties);
    }

    record Handled(String thingId, String tenant, Thread thread) {
        boolean inItsTenant() {
            return tenant != null && thingId.startsWith(tenant + "-");
        }
    }
}
