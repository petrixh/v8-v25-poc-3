/**
 * Copyright (c) 2015 Bosch Software Innovations GmbH and others
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.hawkbit.ui.management.targettag.filter;

import java.io.Serializable;
import java.util.Optional;

import com.vaadin.flow.component.accordion.Accordion;
import com.vaadin.flow.component.accordion.AccordionPanel;
import com.vaadin.flow.function.SerializableConsumer;

/**
 * Keeps exactly one panel of an {@link Accordion} open, as the Vaadin 8
 * accordion did: closing the open panel (clicking its title) opens it again at
 * once, and nothing else happens. Only a switch to another panel is reported.
 */
class OneOpenPanelRule implements Serializable {
    private static final long serialVersionUID = 1L;

    private final Accordion accordion;
    private final SerializableConsumer<AccordionPanel> switchedTo;
    private AccordionPanel current;

    /**
     * @param accordion
     *            the accordion to guard; its opened panel when the rule is
     *            installed is the current one
     * @param switchedTo
     *            called when the user (or the server) opens another panel
     */
    OneOpenPanelRule(final Accordion accordion, final SerializableConsumer<AccordionPanel> switchedTo) {
        this.accordion = accordion;
        this.switchedTo = switchedTo;
        this.current = accordion.getOpenedPanel().orElse(null);
        accordion.addOpenedChangeListener(event -> onOpenedChanged(event.getOpenedPanel()));
    }

    private void onOpenedChanged(final Optional<AccordionPanel> opened) {
        if (!opened.isPresent()) {
            // the open panel was closed: open it again, no filter change
            if (current != null) {
                accordion.open(current);
            }
        } else if (opened.get() != current) {
            current = opened.get();
            switchedTo.accept(current);
        }
        // else: the echo of reopening the current panel
    }
}
