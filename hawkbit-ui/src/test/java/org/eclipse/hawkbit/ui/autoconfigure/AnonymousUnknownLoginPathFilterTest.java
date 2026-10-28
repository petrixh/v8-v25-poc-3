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

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

import io.qameta.allure.Description;
import io.qameta.allure.Feature;
import io.qameta.allure.Story;

/**
 * 1B-58: an unknown address below the login view answers 404 for a user who
 * is not logged in, before Flow's router builds the app shell.
 */
@Feature("Unit Tests - Management UI")
@Story("Anonymous unknown login path")
class AnonymousUnknownLoginPathFilterTest {

    private final AnonymousUnknownLoginPathFilter filter = new AnonymousUnknownLoginPathFilter();

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @Description("Anonymous /UI/login/zzz and deeper unknown paths answer 404 and never reach the router")
    void anonymousUnknownPathAnswers404() throws Exception {
        for (final String path : List.of("/UI/login/zzz", "/UI/login/zzz/deeper", "/UI/login/nosuch.png")) {
            final MockHttpServletResponse response = run(path);
            assertThat(response.getStatus()).as(path).isEqualTo(404);
            assertThat(chainCalled(path)).as("router reached for %s", path).isFalse();
        }
    }

    @Test
    @Description("The login view and Flow's own requests for it pass")
    void loginViewAndFlowRequestsPass() throws Exception {
        for (final String path : List.of("/UI/login", "/UI/login/", "/UI/", "/UI/deployment")) {
            assertThat(run(path).getStatus()).as(path).isEqualTo(200);
            assertThat(chainCalled(path)).as("passed on %s", path).isTrue();
        }
        final MockHttpServletRequest uidl = request("/UI/login/zzz");
        uidl.setParameter("v-r", "uidl");
        final MockFilterChain chain = new MockFilterChain();
        filter.doFilter(uidl, new MockHttpServletResponse(), chain);
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    @Description("A logged-in user gets the router's error view (in the shell), not this answer")
    void loggedInUserPasses() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated("admin", "n/a", AuthorityUtils.createAuthorityList("ROLE_X")));
        assertThat(run("/UI/login/zzz").getStatus()).isEqualTo(200);
        assertThat(chainCalled("/UI/login/zzz")).isTrue();
    }

    @Test
    @Description("An anonymous token (a session with no login) is treated as not logged in")
    void anonymousTokenAnswers404() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken("key", "anonymous",
                AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));
        assertThat(run("/UI/login/zzz").getStatus()).isEqualTo(404);
    }

    private MockHttpServletResponse run(final String path) throws Exception {
        final MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request(path), response, new MockFilterChain());
        return response;
    }

    private boolean chainCalled(final String path) throws Exception {
        final MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request(path), new MockHttpServletResponse(), chain);
        return chain.getRequest() != null;
    }

    private static MockHttpServletRequest request(final String path) {
        // the Flow servlet is mapped to /*: servlet path empty, address in the path info
        final MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        request.setServletPath("");
        request.setPathInfo(path);
        return request;
    }
}
