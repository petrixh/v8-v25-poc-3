/**
 * Copyright (c) 2015 Bosch Software Innovations GmbH and others
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.hawkbit.ui.common.filterlayout;

import org.eclipse.hawkbit.ui.common.EventListenersAwareLayout;
import org.eclipse.hawkbit.ui.common.grid.header.AbstractGridHeader;
import org.eclipse.hawkbit.ui.utils.SPUIDefinitions;

import com.vaadin.flow.component.orderedlayout.FlexComponent.Alignment;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.HasSize;
import com.vaadin.flow.component.HasStyle;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;

/**
 * Parent class for filter button layout.
 */
public abstract class AbstractFilterLayout extends VerticalLayout implements EventListenersAwareLayout {
    private static final long serialVersionUID = 1L;

    protected void buildLayout() {
        setWidth(SPUIDefinitions.FILTER_BY_TYPE_WIDTH + "px");
        setClassName("filter-btns-main-layout");
        setHeight("100%");
        setSpacing(false);
        setPadding(false);

        final Component filterHeader = getFilterHeader();
        final Component filterButtons = getFilterContent();
        // adding border (Vaadin 8: any ComponentContainer; Flow: the
        // component's style and size mixins)
        if (filterButtons instanceof HasStyle styledFilterButtons) {
            styledFilterButtons.addClassNames("filter-btns-layout");
        }
        if (filterButtons instanceof HasSize sizedFilterButtons) {
            sizedFilterButtons.setSizeFull();
        }

        add(filterHeader, filterButtons);

        setHorizontalComponentAlignment(Alignment.CENTER, filterHeader);
        setHorizontalComponentAlignment(Alignment.CENTER, filterButtons);

        setFlexGrow(1.0F, filterButtons);
    }

    protected abstract AbstractGridHeader getFilterHeader();

    protected abstract Component getFilterContent();

    protected static VerticalLayout wrapFilterContent(final Component filterContent) {
        final VerticalLayout filterContentWrapper = new VerticalLayout();
        filterContentWrapper.setPadding(false);
        filterContentWrapper.setSpacing(false);

        filterContentWrapper.add(filterContent);
        filterContentWrapper.setHorizontalComponentAlignment(Alignment.START, filterContent);
        filterContentWrapper.setFlexGrow(1.0F, filterContent);

        return filterContentWrapper;
    }
}
