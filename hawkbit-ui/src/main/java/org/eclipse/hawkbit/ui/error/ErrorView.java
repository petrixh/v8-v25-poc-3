/**
 * Copyright (c) 2015 Bosch Software Innovations GmbH and others
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.hawkbit.ui.error;

import org.eclipse.hawkbit.ui.menu.DashboardMenu;
import org.eclipse.hawkbit.ui.menu.DashboardMenuItem;
import org.eclipse.hawkbit.ui.utils.SPUIStyleDefinitions;
import org.eclipse.hawkbit.ui.utils.UINotification;
import org.eclipse.hawkbit.ui.utils.VaadinMessageSource;
import org.springframework.beans.factory.annotation.Autowired;

import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.ErrorParameter;
import com.vaadin.flow.router.HasErrorParameter;
import com.vaadin.flow.router.NotFoundException;
import com.vaadin.flow.server.HttpStatusCode;
import com.vaadin.flow.spring.annotation.SpringComponent;
import com.vaadin.flow.spring.annotation.UIScope;

import jakarta.annotation.security.PermitAll;

/**
 * View class that is instantiated when no other view matches the navigation
 * state.
 *
 * Vaadin Flow: the error target for {@link NotFoundException} (Vaadin 8:
 * {@code Navigator#setErrorView}). The shell reroutes denied views here too.
 * Flow shows an error target inside a layout only through
 * {@link com.vaadin.flow.router.ParentLayout}, which needs the concrete layout
 * class: a runtime shows this view inside its hawkBit UI layout with a
 * subclass annotated {@code @ParentLayout(<its layout>.class)}.
 */
@SpringComponent
@UIScope
@PermitAll
public class ErrorView extends VerticalLayout implements HasErrorParameter<NotFoundException> {

    private static final long serialVersionUID = 1L;

    private final Span message;

    private final VaadinMessageSource i18n;

    private final DashboardMenu dashboardMenu;

    @Autowired
    protected ErrorView(final VaadinMessageSource i18n, final DashboardMenu dashboardMenu) {
        this.i18n = i18n;
        this.dashboardMenu = dashboardMenu;
        setPadding(true);
        setSpacing(false);

        message = new Span();
        add(message);
    }

    @Override
    public int setErrorParameter(final BeforeEnterEvent event, final ErrorParameter<NotFoundException> parameter) {
        // Vaadin 8: ViewChangeEvent#getViewName(), which for the error view was
        // the whole navigation state (view name and parameters); Flow: the path
        final String viewName = event.getLocation().getPath();
        final DashboardMenuItem view = dashboardMenu.getByViewName(viewName);
        if (view == null) {
            message.setText(i18n.getMessage("message.error.view", viewName));
            return HttpStatusCode.NOT_FOUND.getCode();
        }
        if (dashboardMenu.isAccessDenied(viewName)) {
            UINotification.showNotification(SPUIStyleDefinitions.SP_NOTIFICATION_ERROR_MESSAGE_STYLE,
                    i18n.getMessage("message.accessdenied"),
                    i18n.getMessage("message.accessdenied.view", viewName), null, true);
            message.setText(i18n.getMessage("message.accessdenied.view", viewName));
            // A view the user may not open answers 404, like an unknown
            // path (the old app's page had no HTTP status of its own)
            return HttpStatusCode.NOT_FOUND.getCode();
        }
        return HttpStatusCode.NOT_FOUND.getCode();
    }

}
