/**
 * Copyright (c) 2015 Bosch Software Innovations GmbH and others
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.hawkbit.ui.tenantconfiguration.polling;

import java.time.Duration;

import org.eclipse.hawkbit.ui.utils.VaadinMessageSource;

import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.html.NativeLabel;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.FlexComponent.Alignment;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;

/**
 * The DurationConfigField consists of three vaadin fields. A {@link Span}
 * {@link DurationField} and a {@link Checkbox}. The user can then enter a
 * duration in the DurationField or he can configure using the global duration
 * by changing the Checkbox.
 */
public final class DurationConfigField extends HorizontalLayout {
    private static final long serialVersionUID = 1L;

    private final Checkbox checkBox = new Checkbox();
    private final DurationField durationField = new DurationField();
    private transient Duration globalDuration;

    private DurationConfigField(final String id, final VaadinMessageSource i18n) {
        this.setId(id);
        this.setSpacing(true);
        this.setPadding(false);
        this.addClassNames("duration-config-field");

        durationField.setId(id + ".field");
        checkBox.setId(id + ".checkbox");
        durationField.setEnabled(false);
        durationField.setI18n(i18n);

        this.add(checkBox);
        this.setVerticalComponentAlignment(Alignment.CENTER, checkBox);

        this.add(durationField);
        this.setFlexGrow(1.0F, durationField);
        this.setVerticalComponentAlignment(Alignment.CENTER, durationField);

        checkBox.addValueChangeListener(event -> checkBoxChanged(event.getValue()));
    }

    private void checkBoxChanged(final Boolean isActivated) {
        durationField.setEnabled(isActivated);

        if (!isActivated) {
            durationField.setDuration(globalDuration);
        }
    }

    /**
     * has to be called before using, see Builder Implementation.
     *
     * @param globalDuration
     *            duration value which is stored in the global configuration
     */
    public void setGlobalDuration(final Duration globalDuration) {
        this.globalDuration = globalDuration;
    }

    /**
     * @return Duration config checkbox
     */
    public Checkbox getCheckBox() {
        return checkBox;
    }

    /**
     * @return Duration input field
     */
    public DurationField getDurationField() {
        return durationField;
    }

    /**
     * Create a DurationConfigFieldBuilder.
     *
     * @param id
     *         Config field id
     * @param i18n
     *          VaadinMessageSource
     * @return the builder
     */
    public static DurationConfigFieldBuilder builder(final String id, final VaadinMessageSource i18n) {
        return new DurationConfigFieldBuilder(id, i18n);
    }

    /**
     * Builder for the calendar widget.
     */
    public static final class DurationConfigFieldBuilder {
        private final DurationConfigField field;

        private Duration globalDuration;

        private DurationConfigFieldBuilder(final String id, final VaadinMessageSource i18n) {
            field = new DurationConfigField(id, i18n);
        }

        /**
         * set the checkbox tooltip.
         *
         * @param label
         *            the tooltip
         *
         * @return the builder
         */
        public DurationConfigFieldBuilder checkBoxTooltip(final String label) {
            field.getCheckBox().setTooltipText(label);

            return this;
        }

        /**
         * set the global duration.
         *
         * @param globalDuration
         *            the global duration
         *
         * @return the builder
         */
        public DurationConfigFieldBuilder globalDuration(final Duration globalDuration) {
            this.globalDuration = globalDuration;
            return this;
        }

        /**
         * set the caption.
         *
         * @param caption
         *            the caption
         *
         * @return the builder
         */
        public DurationConfigFieldBuilder caption(final String caption) {
            // Vaadin 8 showed the layout caption above the field; Flow layouts
            // have no caption, so it is the first element of the row
            field.addComponentAsFirst(new NativeLabel(caption));
            return this;
        }

        /**
         * set the range.
         *
         * @param minDuration
         *            min duration
         * @param maxDuration
         *            max duration
         *
         * @return the builder
         */
        public DurationConfigFieldBuilder range(final Duration minDuration, final Duration maxDuration) {
            field.getDurationField().setMinimumDuration(minDuration);
            field.getDurationField().setMaximumDuration(maxDuration);

            return this;
        }

        /**
         * Create the {@link DurationConfigField}.
         *
         * @return the {@link DurationConfigField}
         */
        public DurationConfigField build() {
            if (globalDuration == null) {
                throw new IllegalStateException(
                        "Cannot build DurationConfigField without a value for global duration.");
            }
            field.setGlobalDuration(globalDuration);

            return field;
        }
    }
}
