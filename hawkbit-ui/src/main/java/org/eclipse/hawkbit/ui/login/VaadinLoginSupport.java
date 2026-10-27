/**
 * Copyright (c) 2023 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.hawkbit.ui.login;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;

import com.vaadin.flow.server.VaadinServletRequest;
import com.vaadin.flow.server.VaadinServletResponse;

/**
 * Programmatic login from the login view. Takes the place of vaadin4spring's
 * {@code VaadinSecurity.login(Authentication)} (add-on A3, dropped for Vaadin
 * 25) and does what it did: authenticate the token, store the security context
 * in the HTTP session, then call the success handler, which redirects.
 */
public class VaadinLoginSupport {

    private final AuthenticationManager authenticationManager;
    private final VaadinAuthenticationSuccessHandler successHandler;
    private final SecurityContextRepository securityContextRepository = new HttpSessionSecurityContextRepository();

    /**
     * Constructor.
     *
     * @param authenticationManager
     *            authenticates the login token
     * @param successHandler
     *            called after a successful login
     */
    public VaadinLoginSupport(final AuthenticationManager authenticationManager,
            final VaadinAuthenticationSuccessHandler successHandler) {
        this.authenticationManager = authenticationManager;
        this.successHandler = successHandler;
    }

    /**
     * Authenticates the given token and signs the user in to the current
     * session.
     *
     * @param authentication
     *            the login token (tenant, user and password)
     * @throws Exception
     *             AuthenticationException if the login is refused, or any
     *             exception of the success handler
     */
    public void login(final Authentication authentication) throws Exception {
        final Authentication fullyAuthenticated = authenticationManager.authenticate(authentication);

        final SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
        securityContext.setAuthentication(fullyAuthenticated);
        SecurityContextHolder.setContext(securityContext);
        securityContextRepository.saveContext(securityContext,
                VaadinServletRequest.getCurrent().getHttpServletRequest(),
                VaadinServletResponse.getCurrent().getHttpServletResponse());

        successHandler.onAuthenticationSuccess(fullyAuthenticated);
    }

    /**
     * Called after a successful login from the login view (vaadin4spring's
     * {@code VaadinAuthenticationSuccessHandler}).
     */
    @FunctionalInterface
    public interface VaadinAuthenticationSuccessHandler {

        /**
         * @param authentication
         *            the successful authentication
         * @throws Exception
         *             if the post-login handling fails
         */
        void onAuthenticationSuccess(Authentication authentication) throws Exception;
    }
}
