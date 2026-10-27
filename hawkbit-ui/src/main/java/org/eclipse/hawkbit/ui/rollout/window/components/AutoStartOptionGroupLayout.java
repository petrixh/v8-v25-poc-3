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

import java.util.TimeZone;

import org.eclipse.hawkbit.ui.utils.HawkbitCommonUtil;
import org.eclipse.hawkbit.ui.utils.SPDateTimeUtil;
import org.eclipse.hawkbit.ui.utils.VaadinMessageSource;

import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.shared.ui.datefield.DateTimeResolution;
import com.vaadin.ui.Alignment;
import com.vaadin.ui.DateTimeField;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.ui.RadioButtonGroup;

/**
 * Rollout start types options layout
 */
public class AutoStartOptionGroupLayout extends HorizontalLayout {

    private static final long serialVersionUID = 1L;

    private final VaadinMessageSource i18n;

    private RadioButtonGroup<AutoStartOption> autoStartOptionGroup;

    private DateTimeField startAtDateField;

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
        autoStartOptionGroup.setItemIconGenerator(item -> {
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
        });
        autoStartOptionGroup.setItemCaptionGenerator(item -> {
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
        });
        autoStartOptionGroup.setItemDescriptionGenerator(item -> {
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
        });

        autoStartOptionGroup.setItems(AutoStartOption.values());
        add(autoStartOptionGroup);

        startAtDateField = new DateTimeField();
        startAtDateField.setEnabled(false);
        startAtDateField.setClassName("dist-window-forcedtime");

        final TimeZone tz = SPDateTimeUtil.getBrowserTimeZone();
        startAtDateField.setZoneId(SPDateTimeUtil.getTimeZoneId(tz));
        startAtDateField.setLocale(HawkbitCommonUtil.getCurrentLocale());
        startAtDateField.setResolution(DateTimeResolution.MINUTE);
        startAtDateField.addClassNames("tiny");
        startAtDateField.setWidth("14em");
        add(startAtDateField);
        setComponentAlignment(startAtDateField, Alignment.MIDDLE_LEFT);
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
    public DateTimeField getStartAtDateField() {
        return startAtDateField;
    }

}
