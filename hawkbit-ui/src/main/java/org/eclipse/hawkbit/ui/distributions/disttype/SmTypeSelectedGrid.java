/**
 * Copyright (c) 2020 Bosch.IO GmbH and others
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.hawkbit.ui.distributions.disttype;

import org.eclipse.hawkbit.ui.common.builder.FormComponentBuilder;
import org.eclipse.hawkbit.ui.common.builder.GridComponentBuilder;
import org.eclipse.hawkbit.ui.common.data.proxies.ProxyType;
import org.eclipse.hawkbit.ui.common.grid.AbstractGrid;
import org.eclipse.hawkbit.ui.common.grid.selection.RangeSelectionModel;
import org.eclipse.hawkbit.ui.utils.SPUIDefinitions;
import org.eclipse.hawkbit.ui.utils.UIMessageIdProvider;
import org.eclipse.hawkbit.ui.utils.VaadinMessageSource;

import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.grid.Grid;

/**
 * Software Module Selected Type grid which is shown on the Distribution Type
 * Create/Update popup layout.
 */
public class SmTypeSelectedGrid extends Grid<ProxyType> {
    private static final long serialVersionUID = 1L;

    private static final String STAR = " * ";
    private static final String SM_TYPE_SELECTED_NAME_ID = "smTypeSelectedName";
    private static final String SM_TYPE_SELECTED_MANDATORY = "smTypeSelectedMandatory";

    private final VaadinMessageSource i18n;
    private final transient Runnable mandatoryPropertyChangedCallback;

    /**
     * Constructor for SmTypeSelectedGrid
     *
     * @param i18n
     *            VaadinMessageSource
     * @param mandatoryPropertyChangedCallback
     *            Runnable
     */
    public SmTypeSelectedGrid(final VaadinMessageSource i18n, final Runnable mandatoryPropertyChangedCallback) {
        this.i18n = i18n;
        this.mandatoryPropertyChangedCallback = mandatoryPropertyChangedCallback;

        init();
    }

    private void init() {
        setSizeFull();
        setHeight(null);
        addClassNames("no-horizontal-lines");
        addClassNames("no-stripes");
        addClassNames("no-vertical-lines");
        addClassNames("small");
        // used to deactivate cell text selection by user
        addClassNames(AbstractGrid.MULTI_SELECT_STYLE);

        setId(SPUIDefinitions.TWIN_TABLE_SELECTED_ID);
        new RangeSelectionModel<ProxyType>(i18n).extend(this);

        addColumns();
    }

    private void addColumns() {
        GridComponentBuilder.addColumn(this, ProxyType::getName).setKey(SM_TYPE_SELECTED_NAME_ID)
                .setHeader(i18n.getMessage("header.dist.twintable.selected"))
                .setTooltipGenerator(ProxyType::getDescription);

        GridComponentBuilder.addIconColumn(this, this::buildMandatoryTypeCheckBox, SM_TYPE_SELECTED_MANDATORY, STAR)
                .setTooltipGenerator(smType -> i18n.getMessage(UIMessageIdProvider.TOOLTIP_CHECK_FOR_MANDATORY));
    }

    private Checkbox buildMandatoryTypeCheckBox(final ProxyType smType) {
        final Binder<ProxyType> binder = new Binder<>();
        binder.setBean(smType);
        binder.addValueChangeListener(event -> mandatoryPropertyChangedCallback.run());

        final String id = "selected.sm.type." + smType.getId();
        return FormComponentBuilder.createCheckbox(id, binder, ProxyType::isMandatory, ProxyType::setMandatory);
    }
}
