/**
 * Copyright (c) 2015 Bosch Software Innovations GmbH and others
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.hawkbit.ui.rollout.window.components;

import org.eclipse.hawkbit.ui.utils.HawkbitCommonUtil;
import org.eclipse.hawkbit.ui.utils.VaadinMessageSource;

import com.vaadin.flow.component.Text;
import com.vaadin.flow.component.datetimepicker.DateTimePicker;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.radiobutton.RadioButtonGroup;
import com.vaadin.flow.component.shared.Tooltip;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.function.SerializableFunction;

/**
 * Rollout start types options layout
 */
public class AutoStartOptionGroupLayout extends HorizontalLayout {

    private static final long serialVersionUID = 1L;

    private final VaadinMessageSource i18n;

    private RadioButtonGroup<AutoStartOption> autoStartOptionGroup;

    private DateTimePicker startAtDateField;

    private final String autoStartOptionGroupId;

    /**
     * Instantiates the auto start options layout
     * 
     * @param i18n
     *          the internationalization helper
     * @param autoStartOptionGroupId
     *          Auto start option group id
     */
    public AutoStartOptionGroupLayout(final VaadinMessageSource i18n, final String autoStartOptionGroupId) {
        this.i18n = i18n;
        this.autoStartOptionGroupId = autoStartOptionGroupId;

        setSizeUndefined();

        createOptionGroup();
        addValueChangeListener();
    }

    private void createOptionGroup() {
        autoStartOptionGroup = new RadioButtonGroup<>();
        autoStartOptionGroup.setId(autoStartOptionGroupId);
        autoStartOptionGroup.addClassNames("horizontal");
        autoStartOptionGroup.addClassNames("small");
        final SerializableFunction<AutoStartOption, VaadinIcon> itemIconGenerator = item -> {
            switch (item) {
            case MANUAL:
                return VaadinIcon.HAND;
            case AUTO_START:
                return VaadinIcon.PLAY;
            case SCHEDULED:
                return VaadinIcon.CLOCK;
            default:
                return null;
            }
        };
        final SerializableFunction<AutoStartOption, String> itemCaptionGenerator = item -> {
            switch (item) {
            case MANUAL:
                return i18n.getMessage("caption.rollout.start.manual");
            case AUTO_START:
                return i18n.getMessage("caption.rollout.start.auto");
            case SCHEDULED:
                return i18n.getMessage("caption.rollout.start.scheduled");
            default:
                return null;
            }
        };
        final SerializableFunction<AutoStartOption, String> itemDescriptionGenerator = item -> {
            switch (item) {
            case MANUAL:
                return i18n.getMessage("caption.rollout.start.manual.desc");
            case AUTO_START:
                return i18n.getMessage("caption.rollout.start.auto.desc");
            case SCHEDULED:
                return i18n.getMessage("caption.rollout.start.scheduled.desc");
            default:
                return null;
            }
        };
        // Flow's RadioButtonGroup has no item icon or item description
        // generator: each item is rendered as icon + caption, with the
        // description as the item's tooltip
        autoStartOptionGroup.setRenderer(new ComponentRenderer<>(item -> {
            final Span itemContent = new Span(itemIconGenerator.apply(item).create(),
                    new Text(itemCaptionGenerator.apply(item)));
            Tooltip.forComponent(itemContent).setText(itemDescriptionGenerator.apply(item));
            return itemContent;
        }));

        autoStartOptionGroup.setItems(AutoStartOption.values());
        add(autoStartOptionGroup);

        startAtDateField = new DateTimePicker();
        startAtDateField.setEnabled(false);
        startAtDateField.setClassName("dist-window-forcedtime");

        // Vaadin 8 setZoneId only affected a "z" in the date format (none is
        // set here); the value is a LocalDateTime in both. Minute resolution:
        // Flow's default time step shows hours and minutes
        startAtDateField.setLocale(HawkbitCommonUtil.getCurrentLocale());
        startAtDateField.addClassNames("tiny");
        startAtDateField.setWidth("14em");
        add(startAtDateField);
        setVerticalComponentAlignment(Alignment.CENTER, startAtDateField);
    }

    private void addValueChangeListener() {
        autoStartOptionGroup.addValueChangeListener(
                event -> startAtDateField.setEnabled(AutoStartOption.SCHEDULED == event.getValue()));
    }

    /**
     * Rollout start options
     */
    public enum AutoStartOption {
        MANUAL, AUTO_START, SCHEDULED;

    }

    /**
     * @return Rollout auto start option button
     */
    public RadioButtonGroup<AutoStartOption> getAutoStartOptionGroup() {
        return autoStartOptionGroup;
    }

    /**
     * @return Rollout start date time field
     */
    public DateTimePicker getStartAtDateField() {
        return startAtDateField;
    }

}
