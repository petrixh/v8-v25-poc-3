/**
 * Copyright (c) 2015 Bosch Software Innovations GmbH and others
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.hawkbit.rest;

import jakarta.servlet.http.HttpServletResponse;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.eclipse.hawkbit.rest.exception.ResponseExceptionHandler;
import org.eclipse.hawkbit.rest.filter.ExcludePathAwareShallowETagFilter;
import org.eclipse.hawkbit.rest.util.FilterHttpResponse;
import org.eclipse.hawkbit.rest.util.HttpResponseFactoryBean;
import org.eclipse.hawkbit.rest.util.RequestResponseContextHolder;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import org.springframework.core.Ordered;
import org.springframework.hateoas.Links;
import org.springframework.hateoas.RepresentationModel;
import org.springframework.hateoas.config.EnableHypermediaSupport;
import org.springframework.hateoas.config.EnableHypermediaSupport.HypermediaType;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.filter.UrlHandlerFilter;
import tools.jackson.databind.JacksonModule;
import tools.jackson.databind.module.SimpleModule;

/**
 * Configuration for Rest api.
 */
@Configuration
@EnableHypermediaSupport(type = { HypermediaType.HAL })
public class RestConfiguration {

    /**
     * Create filter for {@link HttpServletResponse}.
     */
    @Bean
    FilterHttpResponse filterHttpResponse() {
        return new FilterHttpResponse();
    }

    /**
     * Create factory bean for {@link HttpServletResponse}.
     */
    @Bean
    FactoryBean<HttpServletResponse> httpResponseFactoryBean() {
        return new HttpResponseFactoryBean();
    }

    /**
     * Create factory bean for {@link HttpServletResponse}.
     */
    @Bean
    @Scope(value = WebApplicationContext.SCOPE_REQUEST)
    RequestResponseContextHolder requestResponseContextHolder() {
        return new RequestResponseContextHolder();
    }

    /**
     * {@link ControllerAdvice} for mapping {@link RuntimeException}s from the
     * repository to {@link HttpStatus} codes.
     */
    @Bean
    ResponseExceptionHandler responseExceptionHandler() {
        return new ResponseExceptionHandler();
    }

    /**
     * Filter registration bean for spring etag filter.
     *
     * @return the spring filter registration bean for registering an etag
     *         filter in the filter chain
     */
    @Bean
    FilterRegistrationBean<ExcludePathAwareShallowETagFilter> eTagFilter() {

        final FilterRegistrationBean<ExcludePathAwareShallowETagFilter> filterRegBean = new FilterRegistrationBean<>();
        // Exclude the URLs for downloading artifacts, so no eTag is generated
        // in the ShallowEtagHeaderFilter, just using the SH1 hash of the
        // artifact itself as 'ETag', because otherwise the file will be copied
        // in memory!
        filterRegBean.setFilter(new ExcludePathAwareShallowETagFilter("/UI/**",
                "/rest/v1/softwaremodules/{smId}/artifacts/{artId}/download",
                "/{tenant}/controller/v1/{controllerId}/softwaremodules/{softwareModuleId}/artifacts/**",
                "/api/v1/downloadserver/**"));

        return filterRegBean;
    }

    /**
     * Filter registration bean that lets the REST APIs answer URLs with a
     * trailing slash (e.g. {@code /rest/v1/targets/}) as they did before Spring
     * Framework 6 removed trailing slash matching from Spring MVC. The request
     * is passed on without the trailing slash. Ordered before the security
     * filters, so they see the same path as the handler mappings.
     *
     * @return the spring filter registration bean for the trailing slash
     *         filter
     */
    @Bean
    FilterRegistrationBean<UrlHandlerFilter> trailingSlashFilter() {
        final FilterRegistrationBean<UrlHandlerFilter> filterRegBean = new FilterRegistrationBean<>(
                UrlHandlerFilter.trailingSlashHandler("/rest/**", "/system/admin/**", "/api/v1/downloadserver/**",
                        "/*/controller/v1/**").wrapRequest().build());
        filterRegBean.setOrder(Ordered.HIGHEST_PRECEDENCE + 1);
        return filterRegBean;
    }

    /**
     * Keeps {@code links} as the last field where a {@link RepresentationModel}
     * is written as plain JSON, not HAL (inside response bodies that are not
     * themselves a {@link RepresentationModel}: the tenant configuration map,
     * tag assignment results). Spring HATEOAS 1 named the property explicitly,
     * which made Jackson write it last; Spring HATEOAS 3 does not, so it came
     * first. Registered with Spring Boot's JSON mapper; HAL output keeps Spring
     * HATEOAS's own mix-in, which its HAL mapper registers after this one.
     *
     * @return the Jackson module with the mix-in
     */
    @Bean
    JacksonModule representationModelLinksLastModule() {
        return new SimpleModule("hawkbit-representation-model-links-last")
                .setMixInAnnotation(RepresentationModel.class, RepresentationModelLinksLast.class);
    }

    /**
     * Mix-in for {@link RepresentationModel}: {@code links} named explicitly,
     * as Spring HATEOAS 1 did.
     */
    abstract static class RepresentationModelLinksLast {

        @JsonProperty("links")
        abstract Links getLinks();
    }
}
