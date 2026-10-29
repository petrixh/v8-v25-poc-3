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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import org.eclipse.hawkbit.im.authentication.SpPermission;
import org.eclipse.hawkbit.repository.test.util.WithUser;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MvcResult;

import io.qameta.allure.Description;
import io.qameta.allure.Feature;
import io.qameta.allure.Story;

/**
 * Field order of the JSON the Management API writes, as before the upgrade to
 * Spring HATEOAS 3: {@code links} last in plain JSON, {@code _links} last in
 * HAL.
 */
@Feature("Integration Test - Management API")
@Story("JSON field order")
class JsonLinksOrderTest extends AbstractSecurityTest {

    @Test
    @Description("Plain JSON (configuration map): links is the last field of each value")
    @WithUser(authorities = { SpPermission.TENANT_CONFIGURATION })
    void plainJsonWritesLinksLast() throws Exception {
        final String body = getBody("/rest/v1/system/configs");

        assertThat(body).contains("\"global\":true,\"links\":[{\"rel\":\"self\"").doesNotContain("{\"links\":");
    }

    @Test
    @Description("HAL (single configuration value): _links is the last field, as before")
    @WithUser(authorities = { SpPermission.TENANT_CONFIGURATION })
    void halWritesLinksLast() throws Exception {
        final String body = getBody("/rest/v1/system/configs/pollingTime");

        assertThat(body).contains("\"global\":true,\"_links\":{\"self\":").doesNotContain("\"links\"");
    }

    private String getBody(final String path) throws Exception {
        final MvcResult result = mvc.perform(get(path)).andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.OK.value());
        return result.getResponse().getContentAsString();
    }
}
