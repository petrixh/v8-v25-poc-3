/**
 * Copyright (c) 2020 Bosch.IO GmbH and others
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.hawkbit.ui.components;

import java.awt.Color;

import org.eclipse.hawkbit.ui.utils.UIComponentIdProvider;

import com.vaadin.flow.component.customfield.CustomField;
import com.vaadin.flow.component.html.Input;
import com.vaadin.flow.data.value.ValueChangeMode;

/**
 * Defines the Layout for the Tag/Type ColorPicker
 * <p>
 * Vaadin Flow (decision A10): the browser's own colour picker, an
 * {@code <input type="color">}, replaces the Vaadin 8 colour picker popup
 * with its gradient, preview and RGB sliders. The value type is
 * {@link java.awt.Color} (Vaadin 8: {@code com.vaadin.shared.ui.colorpicker.Color}).
 */
public class ColorPickerComponent extends CustomField<Color> {
    private static final long serialVersionUID = 1L;

    private final CustomColorPicker colorPickerBtn;

    private Color selectedColor;
    private boolean isColorUpdateInProgress;

    /**
     * Constructor for ColorPickerComponent
     *
     * @param coloPickerBtnId
     *            Id of colour picker button
     * @param coloPickerBtnCaption
     *            Caption for colour picker button
     */
    public ColorPickerComponent(final String coloPickerBtnId, final String coloPickerBtnCaption) {
        this.colorPickerBtn = new CustomColorPicker(coloPickerBtnId, coloPickerBtnCaption);

        init();
        addValueChangeListeners();
    }

    /**
     * Custom colour picker: the browser's colour input
     */
    public class CustomColorPicker extends CustomField<Color> {
        private static final long serialVersionUID = 1L;

        private final Input colorInput = new Input(ValueChangeMode.ON_CHANGE);

        /**
         * Constructor for CustomColorPicker
         *
         * @param id
         *            Id
         * @param caption
         *            Caption
         */
        public CustomColorPicker(final String id, final String caption) {
            super();

            colorInput.setType("color");
            add(colorInput);
            colorInput.addValueChangeListener(event -> updateValue());

            setId(id);
            setLabel(caption);
        }

        @Override
        protected Color generateModelValue() {
            final String hex = colorInput.getValue();
            return hex == null || hex.isEmpty() ? null : Color.decode(hex);
        }

        @Override
        protected void setPresentationValue(final Color color) {
            colorInput.setValue(color == null ? ""
                    : String.format("#%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue()));
        }
    }

    private void init() {
        // the Vaadin 8 popup layout (gradient, preview, sliders) is gone: the
        // browser shows its own picker, so this field has no content
        setId(UIComponentIdProvider.COLOR_PICKER_LAYOUT);
    }

    private void addValueChangeListeners() {
        colorPickerBtn.addValueChangeListener(event -> colorUpdated(event.getValue()));
    }

    private void colorUpdated(final Color newColor) {
        if (!isColorUpdateInProgress) {
            setValue(newColor);
        }
    }

    @Override
    protected Color generateModelValue() {
        return selectedColor;
    }

    @Override
    protected void setPresentationValue(final Color value) {
        isColorUpdateInProgress = true;

        selectedColor = value;

        colorPickerBtn.setValue(selectedColor);

        isColorUpdateInProgress = false;
    }

    /**
     * @return Colour picker button
     */
    public CustomColorPicker getColorPickerBtn() {
        return colorPickerBtn;
    }
}
