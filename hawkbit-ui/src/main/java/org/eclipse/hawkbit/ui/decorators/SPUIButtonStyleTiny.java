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
 * Style for button: Small.
 *
 */
public class SPUIButtonStyleTiny implements SPUIButtonDecorator {

    @Override
    public Button decorate(final Button button, final String style, final boolean setStyle, final VaadinIcon icon) {
        button.addThemeVariants(ButtonVariant.LUMO_SMALL);
        // Set Style
        if (null != style) {
            if (setStyle) {
                button.setClassName(style);
            } else {
                button.addClassNames(StringUtils.tokenizeToStringArray(style, " "));
            }
        }
        // Set icon
        if (null != icon) {
            button.addThemeVariants(ButtonVariant.LUMO_ICON);
            button.setIcon(icon.create());
        }
        return button;
    }

}
