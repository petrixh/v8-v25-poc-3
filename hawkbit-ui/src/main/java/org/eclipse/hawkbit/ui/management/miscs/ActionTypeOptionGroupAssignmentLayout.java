/**
 * Copyright (c) 2020 Bosch.IO GmbH and others
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.hawkbit.ui.management.miscs;

import org.eclipse.hawkbit.repository.model.Action.ActionType;
import org.eclipse.hawkbit.ui.utils.HawkbitCommonUtil;
import org.eclipse.hawkbit.ui.utils.VaadinMessageSource;

import com.vaadin.flow.component.datetimepicker.DateTimePicker;

/**
 * Action type option group layout for manual assignment.
 */
public class ActionTypeOptionGroupAssignmentLayout extends AbstractActionTypeOptionGroupLayout {
    private static final long serialVersionUID = 1L;

    protected static final String STYLE_DIST_WINDOW_FORCEDTIME = "dist-window-forcedtime";

    private DateTimePicker forcedTimeDateField;

    /**
     * Constructor
     * 
     * @param i18n
     *            VaadinMessageSource
     * @param actionTypeOptionGroupId
     *            Id of action type option group
     */
    public ActionTypeOptionGroupAssignmentLayout(final VaadinMessageSource i18n, final String actionTypeOptionGroupId) {
        super(i18n, actionTypeOptionGroupId);

        actionTypeOptionGroup.addClassNames("small");
        addValueChangeListener();
    }

    private void addValueChangeListener() {
        actionTypeOptionGroup.addValueChangeListener(
                event -> forcedTimeDateField.setEnabled(event.getValue() == ActionType.TIMEFORCED));
    }

    @Override
    protected void addOptionGroup() {
        actionTypeOptionGroup.setItems(ActionType.FORCED, ActionType.SOFT, ActionType.DOWNLOAD_ONLY,
                ActionType.TIMEFORCED);
        add(actionTypeOptionGroup);
        addTimeforcedDateTimeField();
    }

    private void addTimeforcedDateTimeField() {
        forcedTimeDateField = new DateTimePicker();
        forcedTimeDateField.setEnabled(false);
        forcedTimeDateField.setClassName(STYLE_DIST_WINDOW_FORCEDTIME);

        // The value is a LocalDateTime as before; Vaadin 8's setZoneId only
        // formatted a zone name, which this field does not show. Minute
        // resolution is the picker's default (hh:mm)
        forcedTimeDateField.setLocale(HawkbitCommonUtil.getCurrentLocale());
        forcedTimeDateField.addClassNames("tiny");
        forcedTimeDateField.setWidth("14em");
        add(forcedTimeDateField);
        setVerticalComponentAlignment(Alignment.CENTER, forcedTimeDateField);
    }

    /**
     * @return Date field for forced time
     */
    public DateTimePicker getForcedTimeDateField() {
        return forcedTimeDateField;
    }
}
