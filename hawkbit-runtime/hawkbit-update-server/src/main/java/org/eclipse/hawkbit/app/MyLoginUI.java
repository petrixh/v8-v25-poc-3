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

import org.eclipse.hawkbit.im.authentication.MultitenancyIndicator;
import org.eclipse.hawkbit.ui.UiProperties;
import org.eclipse.hawkbit.ui.login.AbstractHawkbitLoginUI;
import org.eclipse.hawkbit.ui.login.VaadinLoginSupport;
import org.eclipse.hawkbit.ui.themes.HawkbitTheme;
import org.eclipse.hawkbit.ui.utils.VaadinMessageSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;

/**
 * Example hawkBit login UI implementation.
 * 
 * A {@link Route} annotated class must be present in the classpath for the
 * login path. The easiest way to get an hawkBit login UI running is to extend
 * the {@link AbstractHawkbitLoginUI} and to annotate it with {@link Route} as
 * in this example to the defined {@link HawkbitTheme#LOGIN_UI_PATH}, without
 * the hawkBit UI layout around it.
 */
@Route(value = HawkbitTheme.LOGIN_UI_PATH, autoLayout = false)
@AnonymousAllowed
// Exception squid:MaximumInheritanceDepth - Most of the inheritance comes from
// Vaadin.
@SuppressWarnings({ "squid:MaximumInheritanceDepth" })
public class MyLoginUI extends AbstractHawkbitLoginUI {
    private static final long serialVersionUID = 1L;

    @Autowired
    MyLoginUI(final ApplicationContext context, final VaadinLoginSupport vaadinSecurity,
            final VaadinMessageSource i18n, final UiProperties uiProperties,
            final MultitenancyIndicator multiTenancyIndicator) {
        super(context, vaadinSecurity, i18n, uiProperties, multiTenancyIndicator);
    }

}
