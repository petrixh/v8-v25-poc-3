/**
 * Copyright (c) 2015 Bosch Software Innovations GmbH and others
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.hawkbit.ui.decorators;

import org.springframework.util.StringUtils;

import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;

/**
 * Style for button: Small NoBorder.
 */
public class SPUIButtonStyleNoBorder implements SPUIButtonDecorator {

    /**
     * Decorate Button and return.
     * 
     * @param button
     *            as Button
     * @param style
     *            as String
     * @param setStyle
     *            as String
     * @param icon
     *            as resource
     * @return Button
     */
    @Override
    public Button decorate(final Button button, final String style, final boolean setStyle, final VaadinIcon icon) {
        if (style != null && setStyle) {
            button.addClassNames(StringUtils.tokenizeToStringArray(style, " "));
        }
        button.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        if (icon != null) {
            button.addThemeVariants(ButtonVariant.LUMO_ICON);
            button.addClassName("button-no-border");
            button.setIcon(icon.create());
        }

        return button;
    }

}
