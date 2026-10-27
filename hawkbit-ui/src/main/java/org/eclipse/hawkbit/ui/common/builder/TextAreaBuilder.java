/**
 * Copyright (c) 2015 Bosch Software Innovations GmbH and others
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.hawkbit.ui.common.builder;

import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextAreaVariant;

/**
 * TextArea builder.
 *
 */
public class TextAreaBuilder extends AbstractTextFieldBuilder<TextAreaBuilder, TextArea> {
    private static final int INPUT_DEBOUNCE_TIMEOUT = 500;

    /**
     * Constructor.
     * 
     * @param maxLengthAllowed
     *            for the text area
     */
    public TextAreaBuilder(final int maxLengthAllowed) {
        super(maxLengthAllowed);
    }

    @Override
    protected TextArea createTextComponent() {
        final TextArea textArea = new TextArea();
        textArea.addThemeVariants(TextAreaVariant.LUMO_SMALL);
        textArea.setValueChangeMode(ValueChangeMode.LAZY);
        textArea.setValueChangeTimeout(INPUT_DEBOUNCE_TIMEOUT);

        return textArea;
    }

}
