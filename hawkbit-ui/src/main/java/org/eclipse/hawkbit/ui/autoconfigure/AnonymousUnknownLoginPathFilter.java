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
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import com.vaadin.flow.shared.ApplicationConstants;

/**
 * An unknown address below {@code /UI/login/} (for example
 * {@code /UI/login/zzz}) answers 404 for a user who is not logged in, as the
 * Vaadin 8 UI did (the container's error answer, without the app shell).
 * <p>
 * The whole {@code /UI/login/**} is open to everyone (the login view and
 * Flow's own requests for it), so such an address reaches Flow's router. Flow
 * then shows the not-found error view inside the app shell
 * ({@code MyErrorView}), which needs a logged-in user and, without one,
 * failed with a 500 and two stack traces. The shell must never be built for
 * an anonymous user, so these requests are answered here, before the router.
 * <p>
 * Passed on: the login view itself ({@code /UI/login} and {@code /UI/login/}),
 * Flow's session requests ({@code v-r} is {@code uidl}, {@code heartbeat},
 * {@code push} or {@code i18n}) and everything from a logged-in user (the
 * error view in the shell). Any other {@code v-r} value, and
 * {@code v-r=init}, is an address request like one without {@code v-r}: Flow
 * answers an unknown value with its session-expired 403, which the container
 * turns into a 500, and {@code init} reaches the error view.
 * <p>
 * The login view itself ({@code /UI/login}, {@code /UI/login/}) with a
 * {@code v-r} value that is none of Flow's request types (for example
 * {@code v-r=x}) is passed on without that parameter, so an anonymous user gets
 * the login page as without it, as the Vaadin 8 UI did. With it, Flow finds no
 * session that such a request may create and answers its session-expired 403,
 * which the container turns into a 500.
 */
public class AnonymousUnknownLoginPathFilter extends OncePerRequestFilter {

    private static final String LOGIN_PREFIX = "/UI/login/";

    /**
     * The request types of Flow's session requests (the client of a page that is open): they carry
     * {@code v-r}, and Flow answers them itself. Every other request, with no {@code v-r} or with any other value
     * (including {@code init}, which loads the page), is a request for an address.
     */
    private static final Set<String> FLOW_SESSION_REQUEST_TYPES = Set.of(ApplicationConstants.REQUEST_TYPE_UIDL,
            ApplicationConstants.REQUEST_TYPE_HEARTBEAT, ApplicationConstants.REQUEST_TYPE_PUSH,
            ApplicationConstants.REQUEST_TYPE_TRANSLATION_FILE);

    /**
     * The request types with which Flow creates a session itself (as for a request with no {@code v-r}): they load a
     * page. With any other value that is no session request, Flow answers a request without a session with its
     * session-expired 403.
     */
    private static final Set<String> FLOW_PAGE_REQUEST_TYPES = Set.of(ApplicationConstants.REQUEST_TYPE_INIT,
            ApplicationConstants.REQUEST_TYPE_WEBCOMPONENT_RESYNC);

    @Override
    protected void doFilterInternal(final HttpServletRequest request, final HttpServletResponse response,
            final FilterChain filterChain) throws ServletException, IOException {
        final String path = path(request);
        // isAnonymous() comes before anything that reads the request parameters: a logged-in request never has
        // its parameters read here (on a form POST that parses the body)
        if (isUnknownLoginPath(path) && isAnonymous() && !isFlowSessionRequest(request)) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        if (isLoginView(path) && isAnonymous() && hasUnknownRequestType(request)) {
            filterChain.doFilter(new WithoutRequestType(request), response);
            return;
        }
        filterChain.doFilter(request, response);
    }

    private static String path(final HttpServletRequest request) {
        // servlet path plus path info: the address without the context path
        // (as UISecurityConfigurationAdapter's matcher)
        final String servletPath = request.getServletPath();
        final String pathInfo = request.getPathInfo();
        return (servletPath == null ? "" : servletPath) + (pathInfo == null ? "" : pathInfo);
    }

    private static boolean isUnknownLoginPath(final String path) {
        return path.startsWith(LOGIN_PREFIX) && path.length() > LOGIN_PREFIX.length();
    }

    private static boolean isLoginView(final String path) {
        return path.equals(LOGIN_PREFIX) || path.equals(LOGIN_PREFIX.substring(0, LOGIN_PREFIX.length() - 1));
    }

    // read only once the path matched: on a form-encoded POST it parses the body
    private static boolean isFlowSessionRequest(final HttpServletRequest request) {
        final String requestType = request.getParameter(ApplicationConstants.REQUEST_TYPE_PARAMETER);
        return requestType != null && FLOW_SESSION_REQUEST_TYPES.contains(requestType);
    }

    private static boolean hasUnknownRequestType(final HttpServletRequest request) {
        final String requestType = request.getParameter(ApplicationConstants.REQUEST_TYPE_PARAMETER);
        return requestType != null && !FLOW_SESSION_REQUEST_TYPES.contains(requestType)
                && !FLOW_PAGE_REQUEST_TYPES.contains(requestType);
    }

    private static boolean isAnonymous() {
        // with no session the holder can answer no context at all
        final SecurityContext context = SecurityContextHolder.getContext();
        final Authentication authentication = context == null ? null : context.getAuthentication();
        return authentication == null || authentication instanceof AnonymousAuthenticationToken
                || !authentication.isAuthenticated();
    }

    /**
     * The request without its {@code v-r} parameter: Flow sees a request for the
     * address, as without one. The query string is unchanged.
     */
    static final class WithoutRequestType extends HttpServletRequestWrapper {

        WithoutRequestType(final HttpServletRequest request) {
            super(request);
        }

        @Override
        public String getParameter(final String name) {
            return ApplicationConstants.REQUEST_TYPE_PARAMETER.equals(name) ? null : super.getParameter(name);
        }

        @Override
        public String[] getParameterValues(final String name) {
            return ApplicationConstants.REQUEST_TYPE_PARAMETER.equals(name) ? null : super.getParameterValues(name);
        }

        @Override
        public Map<String, String[]> getParameterMap() {
            final Map<String, String[]> parameters = new LinkedHashMap<>(super.getParameterMap());
            parameters.remove(ApplicationConstants.REQUEST_TYPE_PARAMETER);
            return Collections.unmodifiableMap(parameters);
        }

        @Override
        public Enumeration<String> getParameterNames() {
            return Collections.enumeration(getParameterMap().keySet());
        }
    }
}
