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

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 1B-58: an unknown address below {@code /UI/login/} (for example
 * {@code /UI/login/zzz}) answers 404 for a user who is not logged in, as the
 * Vaadin 8 UI did (the container's error answer, without the app shell).
 * <p>
 * The whole {@code /UI/login/**} is open to everyone (the login view and
 * Flow's own requests for it), so such an address reaches Flow's router. Flow
 * then shows the not-found error view inside the app shell
 * ({@code MyErrorView}, 1b-01), which needs a logged-in user and, without one,
 * failed with a 500 and two stack traces. The shell must never be built for
 * an anonymous user, so these requests are answered here, before the router.
 * <p>
 * Passed on: the login view itself ({@code /UI/login} and {@code /UI/login/}),
 * Flow's own requests (they carry the {@code v-r} parameter) and everything
 * from a logged-in user (the error view in the shell, 1b-01).
 */
public class AnonymousUnknownLoginPathFilter extends OncePerRequestFilter {

    private static final String LOGIN_PREFIX = "/UI/login/";

    private static final String FLOW_REQUEST_PARAMETER = "v-r";

    @Override
    protected void doFilterInternal(final HttpServletRequest request, final HttpServletResponse response,
            final FilterChain filterChain) throws ServletException, IOException {
        if (isUnknownLoginPath(request) && isAnonymous()) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        filterChain.doFilter(request, response);
    }

    private static boolean isUnknownLoginPath(final HttpServletRequest request) {
        // servlet path plus path info: the address without the context path
        // (as UISecurityConfigurationAdapter's matcher)
        final String servletPath = request.getServletPath();
        final String pathInfo = request.getPathInfo();
        final String path = (servletPath == null ? "" : servletPath) + (pathInfo == null ? "" : pathInfo);
        return path.startsWith(LOGIN_PREFIX) && path.length() > LOGIN_PREFIX.length()
                && request.getParameter(FLOW_REQUEST_PARAMETER) == null;
    }

    private static boolean isAnonymous() {
        // with no session the holder can answer no context at all
        final SecurityContext context = SecurityContextHolder.getContext();
        final Authentication authentication = context == null ? null : context.getAuthentication();
        return authentication == null || authentication instanceof AnonymousAuthenticationToken
                || !authentication.isAuthenticated();
    }
}
