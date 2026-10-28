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
import static org.mockito.Mockito.mock;

import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;

import org.eclipse.hawkbit.dmf.json.model.DmfActionUpdateStatus;
import org.eclipse.hawkbit.dmf.json.model.DmfAttributeUpdate;
import org.eclipse.hawkbit.dmf.json.model.DmfAutoConfirmation;
import org.eclipse.hawkbit.dmf.json.model.DmfCreateThing;
import org.eclipse.hawkbit.security.DmfTenantSecurityToken;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.test.util.ReflectionTestUtils;

import io.qameta.allure.Description;
import io.qameta.allure.Feature;
import io.qameta.allure.Story;

/**
 * The other DMF tests use their own converter that trusts all packages. This
 * one uses the converter of the production {@link AmqpConfiguration}.
 */
@Feature("Component Tests - Device Management Federation API")
@Story("Production message converter")
class AmqpConfigurationMessageConverterTest {

    static Stream<Arguments> incomingTypes() {
        return Stream.of(Arguments.of(DmfActionUpdateStatus.class, "{\"actionId\":1,\"actionStatus\":\"RUNNING\"}"),
                Arguments.of(DmfCreateThing.class, "{\"name\":\"thing\"}"),
                Arguments.of(DmfAttributeUpdate.class, "{\"attributes\":{\"a\":\"b\"}}"),
                Arguments.of(DmfAutoConfirmation.class, "{\"initiator\":\"i\"}"),
                Arguments.of(DmfTenantSecurityToken.class, "{\"tenant\":\"DEFAULT\",\"controllerId\":\"c\"}"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("incomingTypes")
    @Description("Every message type hawkBit converts from DMF is accepted by the production message converter")
    void productionConverterConvertsIncomingDmfMessages(final Class<?> type, final String json) {
        final AmqpConfiguration configuration = new AmqpConfiguration();
        ReflectionTestUtils.setField(configuration, "rabbitConnectionFactory", mock(ConnectionFactory.class));
        final BaseAmqpService service = new BaseAmqpService(configuration.rabbitTemplate());

        final MessageProperties properties = new MessageProperties();
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        final Message message = new Message(json.getBytes(StandardCharsets.UTF_8), properties);

        assertThat(service.convertMessage(message, type)).isInstanceOf(type);
    }
}
