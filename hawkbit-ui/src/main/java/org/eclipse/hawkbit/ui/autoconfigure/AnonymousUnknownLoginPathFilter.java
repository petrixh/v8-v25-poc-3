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
import java.util.Set;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import com.vaadin.flow.shared.ApplicationConstants;

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
 * Flow's session requests ({@code v-r} is {@code uidl}, {@code heartbeat},
 * {@code push} or {@code i18n}) and everything from a logged-in user (the
 * error view in the shell, 1b-01). Any other {@code v-r} value, and
 * {@code v-r=init}, is an address request like one without {@code v-r}: Flow
 * answers an unknown value with its session-expired 403, which the container
 * turns into a 500, and {@code init} reaches the error view (1B-60).
 */
public class AnonymousUnknownLoginPathFilter extends OncePerRequestFilter {

    private static final String LOGIN_PREFIX = "/UI/login/";

    /**
     * The request types of Flow's session requests (the client of a page that is open): they carry
     * {@code v-r}, and Flow answers them itself. Every other request, with no {@code v-r} or with any other value
     * (including {@code init}, which loads the page), is a request for an address (1B-60).
     */
    private static final Set<String> FLOW_SESSION_REQUEST_TYPES = Set.of(ApplicationConstants.REQUEST_TYPE_UIDL,
            ApplicationConstants.REQUEST_TYPE_HEARTBEAT, ApplicationConstants.REQUEST_TYPE_PUSH,
            ApplicationConstants.REQUEST_TYPE_TRANSLATION_FILE);

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
        final String requestType = request.getParameter(ApplicationConstants.REQUEST_TYPE_PARAMETER);
        return path.startsWith(LOGIN_PREFIX) && path.length() > LOGIN_PREFIX.length()
                && (requestType == null || !FLOW_SESSION_REQUEST_TYPES.contains(requestType));
    }

    private static boolean isAnonymous() {
        // with no session the holder can answer no context at all
        final SecurityContext context = SecurityContextHolder.getContext();
        final Authentication authentication = context == null ? null : context.getAuthentication();
        return authentication == null || authentication instanceof AnonymousAuthenticationToken
                || !authentication.isAuthenticated();
    }
}
