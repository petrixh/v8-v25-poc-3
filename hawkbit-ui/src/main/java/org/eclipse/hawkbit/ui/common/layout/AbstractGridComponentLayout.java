/**
 * Copyright (c) 2015 Bosch Software Innovations GmbH and others
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.hawkbit.ui.common.layout;

import org.eclipse.hawkbit.ui.common.EventListenersAwareLayout;
import org.eclipse.hawkbit.ui.common.detailslayout.AbstractGridDetailsLayout;
import org.eclipse.hawkbit.ui.common.grid.AbstractGrid;
import org.eclipse.hawkbit.ui.common.grid.header.AbstractDetailsHeader;
import org.eclipse.hawkbit.ui.common.grid.header.AbstractGridHeader;
import org.eclipse.hawkbit.ui.utils.SPUIStyleDefinitions;

import com.vaadin.ui.Alignment;
import com.vaadin.flow.component.Component;
import com.vaadin.ui.Layout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;

/**
 * Abstract grid layout class which builds layout with grid header
 * {@link AbstractGridHeader}, grid {@link AbstractGrid}, optional grid details
 * {@link AbstractGridDetailsLayout} and optional footer.
 */
public abstract class AbstractGridComponentLayout extends VerticalLayout implements EventListenersAwareLayout {
    private static final long serialVersionUID = 1L;

    private Component detailsLayout;

    /**
     * Constructor.
     */
    protected AbstractGridComponentLayout() {
        init();
    }

    protected void init() {
        setSizeFull();
        setSpacing(true);
        setPadding(false);
        setClassName("group");
    }

    /**
     * Initializes this layout that presents a header and a grid.
     */
    protected void buildLayout(final AbstractGridHeader gridHeader, final AbstractGrid<?, ?> grid) {
        final VerticalLayout gridHeaderLayout = new VerticalLayout();
        gridHeaderLayout.setSizeFull();
        gridHeaderLayout.setSpacing(false);
        gridHeaderLayout.setPadding(false);

        gridHeaderLayout.setClassName("table-layout");

        gridHeaderLayout.add(gridHeader);
        gridHeaderLayout.setComponentAlignment(gridHeader, Alignment.TOP_CENTER);

        gridHeaderLayout.add(grid);
        gridHeaderLayout.setComponentAlignment(grid, Alignment.TOP_CENTER);
        gridHeaderLayout.setFlexGrow(1.0F, grid);

        add(gridHeaderLayout);
        setComponentAlignment(gridHeaderLayout, Alignment.TOP_CENTER);
        setFlexGrow(1.0F, gridHeaderLayout);
    }

    /**
     * Initializes this layout that presents a header, a grid, details header
     * and grid details.
     */
    protected void buildLayout(final AbstractGridHeader gridHeader, final AbstractGrid<?, ?> grid,
            final Component detailsLayout) {
        buildLayout(gridHeader, grid);

        add(detailsLayout);
        setComponentAlignment(detailsLayout, Alignment.TOP_CENTER);

        this.detailsLayout = detailsLayout;
    }

    /**
     * Initializes this layout that presents a header, a grid, details header
     * and grid details.
     */
    protected void buildLayout(final AbstractGridHeader gridHeader, final AbstractGrid<?, ?> grid,
            final AbstractDetailsHeader<?> gridDetailsHeader, final AbstractGridDetailsLayout<?> gridDetailsLayout) {
        final VerticalLayout detailsHeaderLayout = new VerticalLayout();
        detailsHeaderLayout.setSizeFull();
        detailsHeaderLayout.setSpacing(false);
        detailsHeaderLayout.setPadding(false);
        detailsHeaderLayout.setHeight(null);
        detailsHeaderLayout.addClassNames(SPUIStyleDefinitions.WIDGET_STYLE);

        detailsHeaderLayout.add(gridDetailsHeader);
        detailsHeaderLayout.setComponentAlignment(gridDetailsHeader, Alignment.TOP_CENTER);

        detailsHeaderLayout.add(gridDetailsLayout);
        detailsHeaderLayout.setComponentAlignment(gridDetailsLayout, Alignment.TOP_CENTER);
        detailsHeaderLayout.setFlexGrow(1.0F, gridDetailsLayout);

        buildLayout(gridHeader, grid, detailsHeaderLayout);
    }

    /**
     * Initializes this layout that presents a header, a grid and a footer.
     */
    protected void buildLayout(final AbstractGridHeader gridHeader, final AbstractGrid<?, ?> grid,
            final AbstractFooterSupport footerSupport) {
        buildLayout(gridHeader, grid);

        final Layout footerLayout = footerSupport.createFooterMessageComponent();
        add(footerLayout);
        setComponentAlignment(footerLayout, Alignment.BOTTOM_CENTER);
    }

    /**
     * Initializes this layout that presents a header, a grid, grid details and
     * a footer.
     */
    protected void buildLayout(final AbstractGridHeader gridHeader, final AbstractGrid<?, ?> grid,
            final AbstractDetailsHeader<?> gridDetailsHeader, final AbstractGridDetailsLayout<?> gridDetailsLayout,
            final AbstractFooterSupport footerSupport) {
        buildLayout(gridHeader, grid, gridDetailsHeader, gridDetailsLayout);

        final Layout footerLayout = footerSupport.createFooterMessageComponent();
        add(footerLayout);
        setComponentAlignment(footerLayout, Alignment.BOTTOM_CENTER);
    }

    protected void showDetailsLayout() {
        if (detailsLayout != null) {
            detailsLayout.setVisible(true);
        }
    }

    protected void hideDetailsLayout() {
        if (detailsLayout != null) {
            detailsLayout.setVisible(false);
        }
    }
}
