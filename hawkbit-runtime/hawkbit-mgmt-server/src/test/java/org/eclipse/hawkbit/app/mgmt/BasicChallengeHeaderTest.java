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

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.eclipse.hawkbit.mgmt.rest.api.MgmtRestConstants;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import io.qameta.allure.Description;
import io.qameta.allure.Feature;
import io.qameta.allure.Story;

@Feature("Integration Test - Security")
@Story("Basic authentication challenge")
class BasicChallengeHeaderTest extends AbstractSecurityTest {

    @Test
    @Description("A request without credentials gets the hawkBit realm and no charset parameter (Spring Security 7 adds one by default)")
    void unauthenticatedChallengeHasNoCharset() throws Exception {
        mvc.perform(get(MgmtRestConstants.BASE_V1_REQUEST_MAPPING + "/targets")).andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Basic realm=\"hawkBit\""));
    }

    @Test
    @Description("A request with wrong credentials gets Spring Security's default realm and no charset parameter")
    void badCredentialsChallengeHasNoCharset() throws Exception {
        mvc.perform(get(MgmtRestConstants.BASE_V1_REQUEST_MAPPING + "/targets").with(httpBasic("admin", "wrong")))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Basic realm=\"Realm\""));
    }
}
