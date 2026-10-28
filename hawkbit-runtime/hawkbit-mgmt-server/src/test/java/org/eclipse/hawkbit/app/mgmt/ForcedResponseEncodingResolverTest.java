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

import org.eclipse.hawkbit.autoconfigure.web.ResponseEncodingAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.filter.CharacterEncodingFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

import io.qameta.allure.Description;
import io.qameta.allure.Feature;
import io.qameta.allure.Story;

@Feature("Unit Tests - Runtime")
@Story("Forced response encoding")
class ForcedResponseEncodingResolverTest {

    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ResponseEncodingAutoConfiguration.class));

    @Test
    @Description("The resolver forces the encoding of the single CharacterEncodingFilter and resolves no exception")
    void forcesEncodingOfSingleFilter() {
        runner.withBean(CharacterEncodingFilter.class, () -> new CharacterEncodingFilter("UTF-8", true, true))
                .run(context -> {
                    final MockHttpServletResponse response = new MockHttpServletResponse();
                    assertThat(context.getBean(HandlerExceptionResolver.class).resolveException(
                            new MockHttpServletRequest(), response, null, new IllegalStateException())).isNull();
                    assertThat(response.getCharacterEncoding()).isEqualTo("UTF-8");
                });
    }

    @Test
    @Description("An application with a second CharacterEncodingFilter bean does not get the original exception replaced")
    void twoFiltersDoNotThrow() {
        runner.withBean("first", CharacterEncodingFilter.class, () -> new CharacterEncodingFilter("UTF-8", true, true))
                .withBean("second", CharacterEncodingFilter.class, () -> new CharacterEncodingFilter("UTF-8", true, true))
                .run(context -> {
                    assertThat(context.getBean(HandlerExceptionResolver.class).resolveException(
                            new MockHttpServletRequest(), new MockHttpServletResponse(), null, new IllegalStateException()))
                            .isNull();
                });
    }
}
