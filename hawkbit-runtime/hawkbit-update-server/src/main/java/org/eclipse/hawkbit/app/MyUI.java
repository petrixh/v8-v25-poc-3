/**
 * Copyright (c) 2015 Bosch Software Innovations GmbH and others
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.hawkbit.app;

import org.eclipse.hawkbit.ui.AbstractHawkbitUI;
import org.eclipse.hawkbit.ui.UiProperties;
import org.eclipse.hawkbit.ui.components.NotificationUnreadButton;
import org.eclipse.hawkbit.ui.menu.DashboardMenu;
import org.eclipse.hawkbit.ui.push.EventPushStrategy;
import org.eclipse.hawkbit.ui.push.UIEventProvider;
import org.eclipse.hawkbit.ui.utils.VaadinMessageSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.vaadin.spring.events.EventBus.UIEventBus;

import com.vaadin.flow.router.Layout;
import com.vaadin.flow.server.ErrorHandler;

/**
 * Example hawkBit UI implementation.
 * 
 * A {@link Layout} annotated class must be present in the classpath. The
 * easiest way to get an hawkBit UI running is to extend the
 * {@link AbstractHawkbitUI} and to annotate it with {@link Layout} as in this
 * example: every hawkBit view is then shown inside it. Push (automatic,
 * WEBSOCKET_XHR transport) is configured on the application class, which is
 * the Vaadin app shell.
 *
 */
@Layout
// Exception squid:MaximumInheritanceDepth - Most of the inheritance comes from
// Vaadin.
@SuppressWarnings({ "squid:MaximumInheritanceDepth" })
public class MyUI extends AbstractHawkbitUI {
    private static final long serialVersionUID = 1L;

    @Autowired
    MyUI(final EventPushStrategy pushStrategy, final UIEventBus eventBus, final UIEventProvider eventProvider,
            final ApplicationContext context, final DashboardMenu dashboardMenu,
            final NotificationUnreadButton notificationUnreadButton, final UiProperties uiProperties,
            final VaadinMessageSource i18n, final ErrorHandler uiErrorHandler) {
        super(pushStrategy, eventBus, eventProvider, context, dashboardMenu, notificationUnreadButton, uiProperties,
                i18n, uiErrorHandler);
    }

}
