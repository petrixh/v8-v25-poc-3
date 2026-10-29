/**
 * Copyright (c) 2023 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.hawkbit.app.vv8ui;

import org.eclipse.hawkbit.ui.error.ErrorView;
import org.eclipse.hawkbit.ui.menu.DashboardMenu;
import org.eclipse.hawkbit.ui.utils.VaadinMessageSource;
import org.springframework.beans.factory.annotation.Autowired;

import com.vaadin.flow.router.ParentLayout;
import com.vaadin.flow.spring.annotation.SpringComponent;
import com.vaadin.flow.spring.annotation.UIScope;

import jakarta.annotation.security.PermitAll;

/**
 * Example hawkBit error view implementation.
 *
 * Shows the hawkBit {@link ErrorView} ("No such view", "No access to view")
 * inside {@link MyUI}, with the menu and the header, as the Vaadin 8 UI did.
 * Flow places an error view in a layout only through {@link ParentLayout}, and
 * {@code @Layout} on {@link MyUI} does not apply to error views. Flow uses
 * this subclass instead of {@link ErrorView} for the same exception.
 */
@SpringComponent
@UIScope
@PermitAll
@ParentLayout(MyUI.class)
// Exception squid:MaximumInheritanceDepth - Most of the inheritance comes from
// Vaadin.
@SuppressWarnings({ "squid:MaximumInheritanceDepth" })
public class MyErrorView extends ErrorView {
    private static final long serialVersionUID = 1L;

    @Autowired
    MyErrorView(final VaadinMessageSource i18n, final DashboardMenu dashboardMenu) {
        super(i18n, dashboardMenu);
    }

}
