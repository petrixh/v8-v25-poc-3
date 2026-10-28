/**
 * Copyright (c) 2023 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.hawkbit.ui.common.builder;

import org.springframework.util.StringUtils;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.HasAriaLabel;
import com.vaadin.flow.component.HasLabel;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.formlayout.FormLayout.ResponsiveStep.LabelsPosition;
import com.vaadin.flow.component.html.Span;

/**
 * The form of a window: one column with the captions to the left of the
 * fields, as the Vaadin 8 {@code FormLayout} showed them.
 * <p>
 * Flow's {@link FormLayout} puts the captions of its fields above them.
 * {@link LabelsPosition#ASIDE} moves only the label of a {@link FormItem}, so
 * every field with a caption that is added to this layout is wrapped in a
 * form item: the caption moves into the item's label (the item shows the
 * required indicator of its field), and the field keeps its caption as its
 * accessible name.
 */
public class WindowFormLayout extends FormLayout {

    private static final long serialVersionUID = 1L;

    /**
     * Constructor: one column, labels aside.
     */
    public WindowFormLayout() {
        super();
        // Vaadin 8 FormLayout: one column; Flow has no spacing/margin here
        setResponsiveSteps(new ResponsiveStep("0", 1, LabelsPosition.ASIDE));
        addClassName("window-form-layout");
    }

    @Override
    public void add(final Component... components) {
        for (final Component component : components) {
            super.add(wrap(component));
        }
    }

    @Override
    public void addComponentAtIndex(final int index, final Component component) {
        super.addComponentAtIndex(index, wrap(component));
    }

    private static Component wrap(final Component component) {
        if (!(component instanceof HasLabel) || !(component instanceof HasAriaLabel) || component instanceof Checkbox) {
            return component;
        }
        final String caption = ((HasLabel) component).getLabel();
        if (!StringUtils.hasText(caption)) {
            return component;
        }

        // the field's own caption is replaced by the form item's label
        ((HasLabel) component).setLabel(null);
        ((HasAriaLabel) component).setAriaLabel(caption);
        return new CaptionedFormItem(component, new Span(caption));
    }

    /**
     * A form item of one field with the caption as its label.
     */
    private static final class CaptionedFormItem extends FormItem {

        private static final long serialVersionUID = 1L;

        private CaptionedFormItem(final Component field, final Component label) {
            super();
            addToLabel(label);
            add(field);
        }
    }
}
