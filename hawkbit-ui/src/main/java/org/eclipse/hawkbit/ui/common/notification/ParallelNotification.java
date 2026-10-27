/**
 * Copyright (c) 2020 Bosch.IO GmbH and others
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.hawkbit.ui.common.notification;

import org.springframework.util.StringUtils;

import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;

/**
 * Creates {@link Notification} that do not hide each other. Vaadin 25
 * notifications stack by themselves (add-on A5: the client-side widget is
 * gone); the caption and description become the notification's content.
 *
 */
public class ParallelNotification extends Notification {
    private static final long serialVersionUID = 1L;

    /**
     * Creates notification
     *
     * @param caption
     *            The message to show
     */
    public ParallelNotification(final String caption) {
        this(caption, null);
    }

    /**
     * Creates notification
     *
     * @param caption
     *            The message caption
     * @param description
     *            The message description
     */
    public ParallelNotification(final String caption, final String description) {
        if (StringUtils.hasText(caption)) {
            final Span captionText = new Span(caption);
            captionText.addClassName("v-Notification-caption");
            add(captionText);
        }
        if (StringUtils.hasText(description)) {
            final Span descriptionText = new Span(description);
            descriptionText.addClassName("v-Notification-description");
            add(descriptionText);
        }
    }

}
