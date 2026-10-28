/**
 * Copyright (c) 2021 Bosch.IO GmbH and others
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.hawkbit.ui.management.targettag.targettype;

import com.vaadin.flow.component.grid.Grid;
import org.eclipse.hawkbit.ui.common.builder.GridComponentBuilder;
import org.eclipse.hawkbit.ui.common.data.proxies.ProxyType;
import org.eclipse.hawkbit.ui.common.grid.AbstractGrid;
import org.eclipse.hawkbit.ui.common.grid.selection.RangeSelectionModel;
import org.eclipse.hawkbit.ui.utils.SPUIDefinitions;
import org.eclipse.hawkbit.ui.utils.UIComponentIdProvider;
import org.eclipse.hawkbit.ui.utils.VaadinMessageSource;

/**
 * Distribution Set Source Type grid which is shown on the Target Type
 * Create/Update popup layout.
 */
public class DsTypeSourceGrid extends Grid<ProxyType> {
    private static final long serialVersionUID = 1L;

    private final VaadinMessageSource i18n;

    /**
     * Constructor for DsTypeSourceGrid
     *
     * @param i18n
     *            VaadinMessageSource
     */
    public DsTypeSourceGrid(final VaadinMessageSource i18n) {
        this.i18n = i18n;

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

        setId(SPUIDefinitions.TWIN_TABLE_SOURCE_ID);
        new RangeSelectionModel<ProxyType>(i18n).extend(this);

        addColumns();
    }

    private void addColumns() {
        GridComponentBuilder.addColumn(this, ProxyType::getName).setKey(UIComponentIdProvider.DIST_TYPE_TABLE_SOURCE_ID)
                .setHeader(i18n.getMessage("header.dt.twintable.available"))
                .setTooltipGenerator(ProxyType::getDescription);
    }
}
