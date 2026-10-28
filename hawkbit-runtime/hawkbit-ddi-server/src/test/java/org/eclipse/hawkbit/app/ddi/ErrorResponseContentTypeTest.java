/**
 * Copyright (c) 2023 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.hawkbit.app.ddi;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;

import org.eclipse.hawkbit.repository.test.util.SharedSqlTestDatabaseExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.hateoas.MediaTypes;
import org.springframework.http.HttpHeaders;

import io.qameta.allure.Description;
import io.qameta.allure.Feature;
import io.qameta.allure.Story;

/**
 * Runs on the servlet container, not MockMvc: the mock response keeps the
 * charset when the Content-Type is reset, Tomcat does not.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT, properties = { "hawkbit.dmf.rabbitmq.enabled=false",
        "spring.flyway.enabled=true", "hawkbit.server.ddi.security.authentication.gatewaytoken.enabled=true",
        "hawkbit.server.ddi.security.authentication.gatewaytoken.key=" + ErrorResponseContentTypeTest.GATEWAY_TOKEN })
@ExtendWith(SharedSqlTestDatabaseExtension.class)
@Feature("Integration Test - DDI API")
@Story("Response Content-Type")
class ErrorResponseContentTypeTest {

    static final String GATEWAY_TOKEN = "content-type-test";

    @LocalServerPort
    private int port;

    @Test
    @Description("An error response of the DDI API keeps the charset forced by spring.servlet.encoding.force")
    void errorResponseContainsCharset() throws Exception {
        final HttpRequest request = HttpRequest
                .newBuilder(URI.create("http://localhost:" + port
                        + "/DEFAULT/controller/v1/content-type-test/deploymentBase/" + Long.MAX_VALUE))
                .header(HttpHeaders.AUTHORIZATION, "GatewayToken " + GATEWAY_TOKEN)
                .header(HttpHeaders.ACCEPT, MediaTypes.HAL_JSON_VALUE).build();

        final HttpResponse<String> response = HttpClient.newHttpClient().send(request, BodyHandlers.ofString());

        assertThat(response.statusCode()).as(response.body()).isBetween(400, 499);
        assertThat(response.body()).contains("\"errorCode\"");
        assertThat(response.headers().firstValue(HttpHeaders.CONTENT_TYPE))
                .hasValue(MediaTypes.HAL_JSON_VALUE + ";charset=UTF-8");
    }
}
