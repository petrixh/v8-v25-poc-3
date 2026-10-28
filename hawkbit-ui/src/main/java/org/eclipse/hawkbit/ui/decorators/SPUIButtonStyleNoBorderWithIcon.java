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
 * Decorator class for a borderless Button with an icon.
 */
public class SPUIButtonStyleNoBorderWithIcon implements SPUIButtonDecorator {

    private Button button;

    @Override
    public Button decorate(final Button button, final String style, final boolean setStyle, final VaadinIcon icon) {

        this.button = button;
        button.setSizeFull();

        button.addClassName("small");
        button.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        setOrAddButtonStyle(style, setStyle);

        setButtonIcon(icon);

        return button;
    }

    private void setOrAddButtonStyle(final String style, final boolean setStyle) {

        if (StringUtils.isEmpty(style)) {
            return;
        }

        if (setStyle) {
            // overwrite all other styles
            button.setClassName(style);
        } else {
            button.addClassNames(StringUtils.tokenizeToStringArray(style, " "));
        }
    }

    private void setButtonIcon(final VaadinIcon icon) {

        if (icon == null) {
            return;
        }
        button.setIcon(icon.create());
    }
}
