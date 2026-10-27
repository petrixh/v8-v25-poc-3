/**
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.hawkbit.ui.autoconfigure;

import java.util.Objects;

import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolderStrategy;

import com.vaadin.flow.spring.security.VaadinAwareSecurityContextHolderStrategy;

/**
 * Vaadin 25: the security context set on the current thread wins over the
 * user of the Vaadin session, as with the thread-local holder of the Vaadin 8
 * UI.
 * <p>
 * vaadin-spring installs {@link VaadinAwareSecurityContextHolderStrategy},
 * which answers with the security context of the HTTP session whenever a
 * Vaadin session is current, and ignores a context set on the thread. Code
 * that switches the context for a block, such as
 * {@code SystemSecurityContext.runAsSystem}, then runs as the logged-in user.
 * This strategy keeps a context set on the thread only while it differs from
 * the one Vaadin's strategy would return; otherwise, and when none was set,
 * it answers as Vaadin's strategy does. Restoring the previous context (the
 * session's) therefore leaves nothing behind on the thread.
 */
public final class ThreadFirstSecurityContextHolderStrategy implements SecurityContextHolderStrategy {

    private static final ThreadLocal<SecurityContext> SWITCHED_CONTEXT = new ThreadLocal<>();

    private final SecurityContextHolderStrategy vaadinStrategy = new VaadinAwareSecurityContextHolderStrategy();

    @Override
    public void clearContext() {
        SWITCHED_CONTEXT.remove();
        vaadinStrategy.clearContext();
    }

    @Override
    public SecurityContext getContext() {
        final SecurityContext switched = SWITCHED_CONTEXT.get();
        return switched != null ? switched : vaadinStrategy.getContext();
    }

    @Override
    public void setContext(final SecurityContext context) {
        Objects.requireNonNull(context, "Only non-null SecurityContext instances are permitted");
        vaadinStrategy.setContext(context);
        if (vaadinStrategy.getContext() == context) {
            SWITCHED_CONTEXT.remove();
        } else {
            SWITCHED_CONTEXT.set(context);
        }
    }

    @Override
    public SecurityContext createEmptyContext() {
        return vaadinStrategy.createEmptyContext();
    }
}
