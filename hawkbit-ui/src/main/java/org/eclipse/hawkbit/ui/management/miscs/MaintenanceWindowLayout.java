/**
 * Copyright (c) 2018 Siemens AG
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package org.eclipse.hawkbit.ui.management.miscs;

import org.eclipse.hawkbit.repository.MaintenanceScheduleHelper;
import org.eclipse.hawkbit.ui.utils.HawkbitCommonUtil;
import org.eclipse.hawkbit.ui.utils.UIComponentIdProvider;
import org.eclipse.hawkbit.ui.utils.UIMessageIdProvider;
import org.eclipse.hawkbit.ui.utils.VaadinMessageSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cronutils.descriptor.CronDescriptor;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;

/**
 * {@link MaintenanceWindowLayout} defines UI layout that is used to specify the
 * maintenance schedule while assigning distribution set(s) to the target(s).
 */
public class MaintenanceWindowLayout extends VerticalLayout {
    private static final long serialVersionUID = 1L;

    private static final Logger LOG = LoggerFactory.getLogger(MaintenanceWindowLayout.class);

    private final VaadinMessageSource i18n;
    private final TextField schedule;
    private final Span scheduleTranslator;
    private final transient CronDescriptor cronDescriptor;

    /**
     * Constructor for the control to specify the maintenance schedule.
     *
     * @param i18n
     *            (@link VaadinMessageSource} to get the localized resource
     *            strings.
     * @param schedule
     *          Schedule
     * @param duration
     *          Duration
     * @param timeZone
     *          Time zone
     * @param scheduleTranslator
     *          Schedule translator
     *
     */
    public MaintenanceWindowLayout(final VaadinMessageSource i18n, final TextField schedule, final TextField duration,
            final ComboBox<String> timeZone, final Span scheduleTranslator) {
        this.i18n = i18n;
        this.schedule = schedule;
        this.scheduleTranslator = scheduleTranslator;
        this.cronDescriptor = CronDescriptor.instance(HawkbitCommonUtil.getCurrentLocale());

        buildLayout(duration, timeZone);
        addValueChangeListeners();
    }

    private void buildLayout(final TextField duration, final ComboBox<String> timeZone) {
        setPadding(false);
        setSpacing(false);
        setClassName("dist-window-maintenance-window-layout");
        setId(UIComponentIdProvider.MAINTENANCE_WINDOW_LAYOUT_ID);

        final HorizontalLayout maintenanceComponentsContainer = new HorizontalLayout();
        maintenanceComponentsContainer.setPadding(false);
        maintenanceComponentsContainer.setSpacing(false);

        maintenanceComponentsContainer.add(schedule);
        maintenanceComponentsContainer.add(duration);
        maintenanceComponentsContainer.add(timeZone);

        add(maintenanceComponentsContainer);

        add(scheduleTranslator);
    }

    private void addValueChangeListeners() {
        schedule.addValueChangeListener(
                event -> scheduleTranslator.setText(translateCronExpression(event.getValue())));
    }

    private String translateCronExpression(final String cronExpression) {
        try {
            return cronDescriptor.describe(MaintenanceScheduleHelper.getCronFromExpression(cronExpression));
        } catch (final IllegalArgumentException ex) {
            LOG.trace("Error in Cron Expression of Maintenance Window in UI: {}", ex.getMessage());
            return i18n.getMessage(UIMessageIdProvider.CRON_VALIDATION_ERROR);
        }
    }
}
