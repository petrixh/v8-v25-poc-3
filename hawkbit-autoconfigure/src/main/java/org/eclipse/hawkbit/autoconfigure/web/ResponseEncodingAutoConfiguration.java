/**
 * Copyright (c) 2023 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.hawkbit.autoconfigure.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.filter.CharacterEncodingFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.ModelAndView;

/**
 * Spring Framework 6.2+ clears the response Content-Type before it handles an
 * exception; on Tomcat that also clears the charset the
 * {@link CharacterEncodingFilter} forced ({@code spring.servlet.encoding.force}).
 * Error responses (hawkBit's REST exception handler and Spring Boot's error
 * JSON) then lost {@code ;charset=UTF-8}. The resolver of this configuration
 * runs first and forces the filter's encoding again, as before.
 */
@Configuration
@ConditionalOnWebApplication(type = Type.SERVLET)
@ConditionalOnClass({ HandlerExceptionResolver.class, CharacterEncodingFilter.class })
public class ResponseEncodingAutoConfiguration {

    @Bean
    HandlerExceptionResolver forcedResponseEncodingExceptionResolver(
            final ObjectProvider<CharacterEncodingFilter> characterEncodingFilter) {
        return new ForcedResponseEncodingExceptionResolver(characterEncodingFilter);
    }

    private static final class ForcedResponseEncodingExceptionResolver implements HandlerExceptionResolver, Ordered {

        private final ObjectProvider<CharacterEncodingFilter> characterEncodingFilter;

        private ForcedResponseEncodingExceptionResolver(
                final ObjectProvider<CharacterEncodingFilter> characterEncodingFilter) {
            this.characterEncodingFilter = characterEncodingFilter;
        }

        @Override
        public ModelAndView resolveException(final HttpServletRequest request, final HttpServletResponse response,
                final Object handler, final Exception ex) {
            final CharacterEncodingFilter filter = characterEncodingFilter.getIfAvailable();
            if (filter != null && filter.isForceResponseEncoding() && filter.getEncoding() != null) {
                response.setCharacterEncoding(filter.getEncoding());
            }
            // not resolved: the next resolvers handle the exception
            return null;
        }

        @Override
        public int getOrder() {
            return Ordered.HIGHEST_PRECEDENCE;
        }
    }
}
