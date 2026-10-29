/**
 * Copyright (c) 2023 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.hawkbit.rest.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import io.qameta.allure.Description;
import io.qameta.allure.Feature;
import io.qameta.allure.Story;

/**
 * A 406 answer carries no {@code Accept} response header (Spring
 * Framework 7 adds one by default; the old app's answers had none).
 */
@Feature("Unit Tests - REST")
@Story("406 Not Acceptable answer")
class ResponseExceptionHandlerNotAcceptableTest {

    @RestController
    static class JsonOnlyController {

        @GetMapping(path = "/json", produces = MediaType.APPLICATION_JSON_VALUE)
        String json() {
            return "{}";
        }
    }

    @Test
    @Description("An Accept header the resource cannot satisfy answers 406 without an Accept response header")
    void notAcceptableHasNoAcceptHeader() throws Exception {
        final MockMvc mvc = MockMvcBuilders.standaloneSetup(new JsonOnlyController())
                .setControllerAdvice(new ResponseExceptionHandler()).build();

        final MvcResult result = mvc.perform(get("/json").accept(MediaType.APPLICATION_XML)).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(406);
        assertThat(result.getResponse().containsHeader("Accept")).as("Accept response header").isFalse();
    }

    @Test
    @Description("An acceptable request is not touched")
    void acceptableRequestAnswers200() throws Exception {
        final MockMvc mvc = MockMvcBuilders.standaloneSetup(new JsonOnlyController())
                .setControllerAdvice(new ResponseExceptionHandler()).build();

        final MvcResult result = mvc.perform(get("/json").accept(MediaType.APPLICATION_JSON)).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
    }
}
