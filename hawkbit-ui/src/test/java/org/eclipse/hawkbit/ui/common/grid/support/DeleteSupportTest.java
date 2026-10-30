/**
 * Copyright (c) 2023 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.hawkbit.ui.common.grid.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.util.Set;

import org.eclipse.hawkbit.ui.common.data.proxies.ProxyDistributionSet;
import org.eclipse.hawkbit.ui.utils.UINotification;
import org.eclipse.hawkbit.ui.utils.VaadinMessageSource;
import org.junit.jupiter.api.Test;

import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.Grid.SelectionMode;

import io.qameta.allure.Feature;
import io.qameta.allure.Story;

@Feature("Unit Tests - Management UI")
@Story("A deletion works on the selected rows as the grid last fetched them")
class DeleteSupportTest {

    @Test
    void selectedItemsForDeletionAreTakenAsLastFetched() {
        final ProxyDistributionSet selected = distributionSet(1L, "Old name");
        final Grid<ProxyDistributionSet> grid = new Grid<>();
        grid.setItems(selected);
        grid.setSelectionMode(SelectionMode.MULTI);
        grid.select(selected);

        // the row was fetched again after the set was renamed elsewhere
        final ProxyDistributionSet fetched = distributionSet(1L, "New name");
        grid.getDataCommunicator().getKeyMapper().key(fetched);
        grid.getDataCommunicator().getKeyMapper().refresh(fetched);

        final DeleteSupport<ProxyDistributionSet> support = new DeleteSupport<>(grid, mock(VaadinMessageSource.class),
                mock(UINotification.class), "a", "b", ProxyDistributionSet::getNameVersion, items -> true, "id");

        final Set<ProxyDistributionSet> items = support.getItemsForDeletion(fetched);
        assertThat(items).singleElement().isSameAs(fetched);
        assertThat(items).singleElement().extracting(ProxyDistributionSet::getNameVersion).isEqualTo("New name");
    }

    @Test
    void clickedItemOutsideTheSelectionIsDeletedAlone() {
        final ProxyDistributionSet selected = distributionSet(1L, "Selected");
        final ProxyDistributionSet clicked = distributionSet(2L, "Clicked");
        final Grid<ProxyDistributionSet> grid = new Grid<>();
        grid.setItems(selected, clicked);
        grid.setSelectionMode(SelectionMode.MULTI);
        grid.select(selected);

        final DeleteSupport<ProxyDistributionSet> support = new DeleteSupport<>(grid, mock(VaadinMessageSource.class),
                mock(UINotification.class), "a", "b", ProxyDistributionSet::getNameVersion, items -> true, "id");

        assertThat(support.getItemsForDeletion(clicked)).containsExactly(clicked);
        assertThat(grid.getSelectedItems()).containsExactly(clicked);
    }

    private static ProxyDistributionSet distributionSet(final Long id, final String nameVersion) {
        final ProxyDistributionSet ds = new ProxyDistributionSet();
        ds.setId(id);
        ds.setNameVersion(nameVersion);
        return ds;
    }
}
