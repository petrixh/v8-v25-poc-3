/**
 * Copyright (c) 2021 Bosch.IO GmbH and others
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.hawkbit.ui.autoconfigure;

import org.eclipse.hawkbit.im.authentication.TenantAwareAuthenticationDetails;
import org.eclipse.hawkbit.repository.SystemManagement;
import org.eclipse.hawkbit.security.DosFilter;
import org.eclipse.hawkbit.security.HawkbitSecurityProperties;
import org.eclipse.hawkbit.security.SystemSecurityContext;
import org.eclipse.hawkbit.ui.MgmtUiConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.boot.web.servlet.ServletListenerRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.WebSecurityConfigurer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.security.web.authentication.logout.LogoutHandler;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
import org.springframework.security.web.session.HttpSessionEventPublisher;
import org.springframework.security.web.util.UrlUtils;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.server.VaadinServletRequest;
import com.vaadin.flow.server.VaadinServletResponse;
import com.vaadin.flow.spring.security.VaadinSecurityConfigurer;

import org.eclipse.hawkbit.ui.login.VaadinLoginSupport;

import java.util.Arrays;

/**
 * {@link WebSecurityConfigurer} for external (management) access.
 */
@Configuration
@EnableWebSecurity
@ConditionalOnClass(MgmtUiConfiguration.class)
public class UISecurityConfigurationAdapter {

    private static final Logger LOG = LoggerFactory.getLogger(UISecurityConfigurationAdapter.class);

    private static final int DOS_FILTER_ORDER = -200;

    @Autowired
    private HawkbitSecurityProperties hawkbitSecurityProperties;

    /**
     * Filter to protect the hawkBit management UI against to many requests.
     *
     * @param securityProperties for filter configuration
     * @return the spring filter registration bean for registering a denial
     * of service protection filter in the filter chain
     */
    @Bean
    @ConditionalOnProperty(prefix = "hawkbit.server.security.dos.ui-filter", name = "enabled", matchIfMissing = true)
    public FilterRegistrationBean<DosFilter> dosMgmtUiFilter(final HawkbitSecurityProperties securityProperties) {
        final HawkbitSecurityProperties.Dos.Filter filterProperties = securityProperties.getDos().getUiFilter();
        final HawkbitSecurityProperties.Clients clientProperties = securityProperties.getClients();

        final FilterRegistrationBean<DosFilter> filterRegBean = new FilterRegistrationBean<>();

        filterRegBean.setFilter(new DosFilter(null, filterProperties.getMaxRead(),
                filterProperties.getMaxWrite(), filterProperties.getWhitelist(), clientProperties.getBlacklist(),
                clientProperties.getRemoteIpHeader()));

        // All URLs that can be called anonymous
        filterRegBean.setUrlPatterns(Arrays.asList("/UI/login", "/UI/login/*", "/UI/logout", "/UI/logout/*"));
        filterRegBean.setOrder(DOS_FILTER_ORDER);
        filterRegBean.setName("dosMgmtUiFilter");

        return filterRegBean;
    }

    @Bean
    AuthenticationManager authenticationManager(final AuthenticationConfiguration authenticationConfiguration) throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }

    /**
     * The success handler of the UI login (replaces the
     * VaadinAuthenticationSuccessHandler of vaadin4spring's shared security,
     * A3). Not a Spring Security {@link AuthenticationSuccessHandler} bean, so
     * the OIDC detection in {@link #filterChainUI} is not affected.
     *
     * @return the vaadin success authentication handler
     */
    @Primary
    @Bean
    public VaadinLoginSupport.VaadinAuthenticationSuccessHandler redirectSaveHandler() {
        final TenantMetadataSavedRequestAwareVaadinAuthenticationSuccessHandler handler = new TenantMetadataSavedRequestAwareVaadinAuthenticationSuccessHandler(
                "/UI/");
        handler.setTargetUrlParameter("r");

        return handler;
    }

    /**
     * Programmatic login of the UI login view (replaces vaadin4spring's
     * VaadinSecurity, A3).
     *
     * @param authenticationManager
     *            the authentication manager
     * @param successHandler
     *            the vaadin success authentication handler
     * @return the login support bean
     */
    @Bean
    public VaadinLoginSupport vaadinLoginSupport(final AuthenticationManager authenticationManager,
            final VaadinLoginSupport.VaadinAuthenticationSuccessHandler successHandler) {
        return new VaadinLoginSupport(authenticationManager, successHandler);
    }

    /**
     * The security context holder strategy that vaadin-spring installs
     * globally (its own bean backs off, or this one is primary): a context set
     * on the thread, e.g. by
     * {@link SystemSecurityContext#runAsSystem(java.util.concurrent.Callable)},
     * wins over the Vaadin session's user, as before Vaadin 25.
     *
     * @return the strategy
     */
    @Primary
    @Bean
    public SecurityContextHolderStrategy threadFirstSecurityContextHolderStrategy() {
        return new ThreadFirstSecurityContextHolderStrategy();
    }

    /**
     * Listener to redirect to login page after session timeout. Close the
     * vaadin session, because it's is not possible to redirect in
     * atmosphere.
     *
     * @return the servlet listener.
     */
    @Bean
    public ServletListenerRegistrationBean<HttpSessionEventPublisher> httpSessionEventPublisher() {
        return new ServletListenerRegistrationBean<>(new HttpSessionEventPublisher());
    }

    @Bean
    @Order(400)
    protected SecurityFilterChain filterChainUI(
            final HttpSecurity http,
            @Autowired(required = false)
            final OAuth2UserService<OidcUserRequest, OidcUser> oidcUserService,
            @Autowired(required = false)
            final AuthenticationSuccessHandler authenticationSuccessHandler,
            final LogoutHandler logoutHandler,
            final LogoutSuccessHandler logoutSuccessHandler)
            throws Exception {
        final boolean enableOidc = oidcUserService != null && authenticationSuccessHandler != null;

        // workaround regex: we need to exclude the URL /UI/HEARTBEAT here
        // because we bound the vaadin application to /UI and not to root,
        // described in vaadin-forum:
        // https://vaadin.com/forum#!/thread/3200565.
        HttpSecurity httpSec;
        if (enableOidc) {
            httpSec = http.securityMatcher(
                    new OrRequestMatcher(antPathRequestMatcher("/**/UI/**"), antPathRequestMatcher("/**/oauth2/**")));
        } else {
            httpSec = http.securityMatcher(antPathRequestMatcher("/**/UI/**"));
        }
        // Vaadin 25 security integration (A3): lets the framework's internal
        // requests (UIDL, push, heartbeat, static resources) through and keeps
        // the navigation access control; no form login (the login view logs in
        // programmatically, as before), CSRF and logout stay as configured here
        // Any other address under /UI needs only a logged-in user (the
        // configurer's default denies it with 403), so an unknown path reaches
        // the router and gets the error view with 404
        httpSec.with(VaadinSecurityConfigurer.vaadin(), vaadin -> vaadin.enableCsrfConfiguration(false)
                .enableLogoutConfiguration(false).anyRequest(anyRequest -> anyRequest.authenticated()));
        // disable as CSRF is handled by Vaadin
        httpSec.csrf(AbstractHttpConfigurer::disable);
        // allow same origin X-Frame-Options for correct file download under
        // Safari
        httpSec.headers(headers -> headers.frameOptions(frameOptions -> frameOptions.sameOrigin()));

        if (hawkbitSecurityProperties.isRequireSsl()) {
            httpSec = httpSec.requiresChannel(crmRegistry -> crmRegistry.anyRequest().requiresSecure());
        } else {
            LOG.info(
                    """
                    ******************
                    ** Requires HTTPS Security has been disabled for UI, should only be used for developing purposes **
                    ******************""");
        }

        if (!ObjectUtils.isEmpty(hawkbitSecurityProperties.getContentSecurityPolicy())) {
            httpSec.headers(headers -> headers
                    .contentSecurityPolicy(csp -> csp.policyDirectives(hawkbitSecurityProperties.getContentSecurityPolicy())));
        }

        // UI: the login view; Vaadin's UIDL requests are let through by the
        // Vaadin configurer, which also requires authentication for any other
        // request
        httpSec.authorizeHttpRequests(requests -> requests.requestMatchers("/UI/login/**").permitAll());
        // An unknown address below /UI/login/ answers 404 for an
        // anonymous user, as before, instead of reaching the router (the error
        // view in the shell needs a logged-in user)
        httpSec.addFilterBefore(new AnonymousUnknownLoginPathFilter(), AuthorizationFilter.class);

        if (enableOidc) {
            // OIDC
            httpSec.oauth2Login(oauth2 -> oauth2.userInfoEndpoint(userInfo -> userInfo.oidcUserService(oidcUserService))
                    .successHandler(authenticationSuccessHandler)).oauth2Client(Customizer.withDefaults());
        } else {
            // UI login / Basic auth
            httpSec.exceptionHandling(exceptionHandling -> exceptionHandling
                    .authenticationEntryPoint(new LoginUrlAuthenticationEntryPoint("/UI/login")));
        }

        // UI logout
        httpSec.logout(logout -> logout.logoutUrl("/UI/logout*").addLogoutHandler(logoutHandler)
                .logoutSuccessHandler(logoutSuccessHandler));

        return httpSec.build();
    }

    @Bean
    public WebSecurityCustomizer webSecurityCustomizer() {
        // No security for static content
        return (web) -> web.ignoring().requestMatchers("/documentation/**", "/VAADIN/**", "/*.*", "/docs/**");
    }

    /**
     * Matches like Spring Security 5's AntPathRequestMatcher (removed in Spring
     * Security 7) did: Ant pattern against the servlet path plus path info.
     * Used where the pattern is not a valid PathPattern ('**' before the end).
     * Same as in SecurityManagedConfiguration.
     */
    private static RequestMatcher antPathRequestMatcher(final String pattern) {
        final AntPathMatcher antPathMatcher = new AntPathMatcher();
        antPathMatcher.setTrimTokens(false);
        return request -> {
            String url = request.getServletPath();
            final String pathInfo = request.getPathInfo();
            if (pathInfo != null) {
                url = StringUtils.hasLength(url) ? url + pathInfo : pathInfo;
            }
            return antPathMatcher.match(pattern, url);
        };
    }

    /**
     * Proxy-based method security for the UI. Notice: we can not use the
     * top-level method security configuration because AdviceMode.ASPECTJ is
     * not supported. Spring Security 7: {@code @EnableGlobalMethodSecurity}
     * needs the removed AccessDecisionManager, so the same switches move to
     * {@code @EnableMethodSecurity}; the AccessDecisionManager bean was only
     * used by vaadin4spring (A3) and is gone with it.
     */
    @Configuration
    @EnableMethodSecurity(securedEnabled = true, prePostEnabled = true, proxyTargetClass = true)
    @ConditionalOnClass(MgmtUiConfiguration.class)
    static class UIMethodSecurity {
    }

    /**
     * After a successful login on the UI we need to ensure to create the tenant
     * meta data within SP.
     */
    class TenantMetadataSavedRequestAwareVaadinAuthenticationSuccessHandler
            implements VaadinLoginSupport.VaadinAuthenticationSuccessHandler {

        @Autowired
        private SystemManagement systemManagement;

        @Autowired
        private SystemSecurityContext systemSecurityContext;

        // what vaadin4spring's VaadinUrlAuthenticationSuccessHandler did: the
        // target URL parameter, else the default target URL (it did not use
        // the saved request, so the user lands on the start view as before);
        // the redirect goes through the browser (Page.setLocation)
        // because the login runs inside a Vaadin request
        private final SimpleUrlAuthenticationSuccessHandler redirectHandler = new SimpleUrlAuthenticationSuccessHandler();

        public TenantMetadataSavedRequestAwareVaadinAuthenticationSuccessHandler(final String defaultTargetUrl) {
            redirectHandler.setDefaultTargetUrl(defaultTargetUrl);
            // relative targets get the servlet context path, absolute ones stay
            // as they are (as vaadin4spring's DefaultVaadinRedirectStrategy did)
            redirectHandler.setRedirectStrategy((request, response, url) -> UI.getCurrent().getPage()
                    .setLocation(UrlUtils.isAbsoluteUrl(url) ? url : request.getContextPath() + url));
        }

        public void setTargetUrlParameter(final String targetUrlParameter) {
            redirectHandler.setTargetUrlParameter(targetUrlParameter);
        }

        @Override
        public void onAuthenticationSuccess(final Authentication authentication) throws Exception {
            systemSecurityContext.runAsSystemAsTenant(systemManagement::getTenantMetadata, getTenantFrom(authentication));

            redirectHandler.onAuthenticationSuccess(VaadinServletRequest.getCurrent().getHttpServletRequest(),
                    VaadinServletResponse.getCurrent().getHttpServletResponse(), authentication);
        }

        private static String getTenantFrom(final Authentication authentication) {
            final Object details = authentication.getDetails();
            if (details instanceof TenantAwareAuthenticationDetails) {
                return ((TenantAwareAuthenticationDetails) details).getTenant();
            }

            throw new InsufficientAuthenticationException("Authentication details/tenant info are not specified!");
        }
    }
}
