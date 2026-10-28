/**
 * Copyright (c) 2023 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.hawkbit.autoconfigure.scheduling;

import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;

/**
 * Spring Security 6+: the {@code DelegatingSecurityContext*} executors keep
 * the {@link SecurityContextHolderStrategy} that is global when they are
 * built. The UI installs its own strategy later (vaadin-spring, once the
 * singletons exist), so executors built before then would read and write a
 * holder that no application code uses: tasks would run with no user and no
 * tenant.
 * <p>
 * This strategy forwards every call to the strategy that is global at the
 * time of the call, as the executors of Spring Security 5 did through the
 * static {@link SecurityContextHolder} methods.
 */
final class GlobalSecurityContextHolderStrategy implements SecurityContextHolderStrategy {

    @Override
    public void clearContext() {
        SecurityContextHolder.getContextHolderStrategy().clearContext();
    }

    @Override
    public SecurityContext getContext() {
        return SecurityContextHolder.getContextHolderStrategy().getContext();
    }

    @Override
    public void setContext(final SecurityContext context) {
        SecurityContextHolder.getContextHolderStrategy().setContext(context);
    }

    @Override
    public SecurityContext createEmptyContext() {
        return SecurityContextHolder.getContextHolderStrategy().createEmptyContext();
    }
}
