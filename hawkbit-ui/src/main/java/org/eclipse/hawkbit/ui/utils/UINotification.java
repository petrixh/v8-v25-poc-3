/**
 * Copyright (c) 2015 Bosch Software Innovations GmbH and others
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.hawkbit.ui.utils;

import java.io.Serializable;

import org.eclipse.hawkbit.ui.common.notification.ParallelNotification;

import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification.Position;
import com.vaadin.flow.spring.annotation.SpringComponent;
import com.vaadin.flow.spring.annotation.UIScope;

/**
 * Show success, warning and error messages.
 */
@UIScope
@SpringComponent
public class UINotification implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Display success type of notification message.
     * 
     * @param message
     *            is the message to displayed as success.
     */
    public void displaySuccess(final String message) {
        showNotification(SPUIStyleDefinitions.SP_NOTIFICATION_SUCCESS_MESSAGE_STYLE, message, VaadinIcon.CHECK_CIRCLE);
    }

    /**
     * Display warning type of notification message.
     * 
     * @param message
     *            is the message to displayed as warning.
     */
    public void displayWarning(final String message) {
        showNotification(SPUIStyleDefinitions.SP_NOTIFICATION_WARNING_MESSAGE_STYLE, message, VaadinIcon.WARNING);
    }

    /**
     * Display error type of notification message.
     * 
     * @param message
     *            as message.
     */
    public void displayValidationError(final String message) {
        showNotification(SPUIStyleDefinitions.SP_NOTIFICATION_ERROR_MESSAGE_STYLE, message,
                VaadinIcon.EXCLAMATION_CIRCLE);
    }

    /**
     * Display generic notification.
     * 
     * @param styleName
     *            Style of the message
     * @param description
     *            Description of the message
     * @param icon
     *            Icon of the message
     */
    public static void showNotification(final String styleName, final String description, final VaadinIcon icon) {
        showNotification(styleName, null, description, icon, true);
    }

    /**
     * Display generic notification.
     * 
     * @param styleName
     *            Style of the message
     * @param caption
     *            Caption of the message
     * @param description
     *            Description of the message
     * @param icon
     *            Icon of the message
     * @param autoClose
     *            Should notification be automatically closed
     */
    public static void showNotification(final String styleName, final String caption, final String description,
            final VaadinIcon icon, final Boolean autoClose) {
        buildNotification(styleName, caption, description, icon, autoClose).open();
    }

    /**
     * Builds UI notification.
     * 
     * @param styleName
     *            Style of the message
     * @param caption
     *            Caption of the message
     * @param description
     *            Description of the message
     * @param icon
     *            Icon of the message
     * @param autoClose
     *            Should notification be automatically closed
     * @return UI notification to be shown
     */
    public static ParallelNotification buildNotification(final String styleName, final String caption,
            final String description, final VaadinIcon icon, final Boolean autoClose) {
        final ParallelNotification notification = new ParallelNotification(caption, description);

        if (icon != null) {
            // Flow notifications have no icon property: the icon goes first
            // in the content, before caption and description
            notification.addComponentAtIndex(0, icon.create());
        }
        // space-separated style names, as before; text content is never HTML
        notification.setClassName(styleName);
        notification.setPosition(Position.BOTTOM_END);

        if (autoClose) {
            notification.setDuration(SPUILabelDefinitions.SP_DELAY);
        } else {
            // 0: stays open until closed (Vaadin 8: -1)
            notification.setDuration(0);
        }

        return notification;
    }
}
