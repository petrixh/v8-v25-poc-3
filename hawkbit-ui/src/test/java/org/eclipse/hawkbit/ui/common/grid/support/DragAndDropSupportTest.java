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

import java.util.List;

import org.eclipse.hawkbit.ui.common.data.proxies.ProxyDistributionSet;
import org.junit.jupiter.api.Test;

import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.Grid.SelectionMode;
import com.vaadin.flow.data.provider.DataKeyMapper;

import io.qameta.allure.Feature;
import io.qameta.allure.Story;

/**
 * A drag of selected rows carries the rows' state as the grid last fetched
 * it, as in Vaadin 8, not the state at the time they were selected: a
 * distribution set selected before it was invalidated is refused on drop
 * (2B-19).
 */
@Feature("Unit Tests - Management UI")
@Story("Users cannot assign a distribution set that was invalidated after they selected it")
class DragAndDropSupportTest {

    @Test
    void selectedItemIsTakenAsLastFetched() {
        final ProxyDistributionSet selected = distributionSet(1L, true);
        final Grid<ProxyDistributionSet> grid = gridWithSelected(selected);

        // the grid fetches the row again after the set was invalidated (Flow's
        // DataCommunicator registers and refreshes each fetched item)
        final ProxyDistributionSet fetched = distributionSet(1L, false);
        final DataKeyMapper<ProxyDistributionSet> keyMapper = grid.getDataCommunicator().getKeyMapper();
        keyMapper.key(fetched);
        keyMapper.refresh(fetched);

        // Flow's selection keeps the instance that was selected
        assertThat(grid.getSelectedItems()).singleElement().extracting(ProxyDistributionSet::getIsValid)
                .isEqualTo(true);

        final List<ProxyDistributionSet> items = DragAndDropSupport.getSelectedItemsAsLastFetched(grid);
        assertThat(items).singleElement().isSameAs(fetched);
        assertThat(items.get(0).getIsValid()).isFalse();
    }

    @Test
    void selectedItemNotFetchedIsKept() {
        final ProxyDistributionSet selected = distributionSet(2L, true);
        final Grid<ProxyDistributionSet> grid = gridWithSelected(selected);

        assertThat(DragAndDropSupport.getSelectedItemsAsLastFetched(grid)).singleElement().isSameAs(selected);
    }

    @Test
    void selectionOrderIsKept() {
        final ProxyDistributionSet first = distributionSet(3L, true);
        final ProxyDistributionSet second = distributionSet(4L, true);
        final Grid<ProxyDistributionSet> grid = new Grid<>();
        grid.setItems(first, second);
        grid.setSelectionMode(SelectionMode.MULTI);
        grid.select(first);
        grid.select(second);

        final ProxyDistributionSet secondFetched = distributionSet(4L, false);
        grid.getDataCommunicator().getKeyMapper().key(secondFetched);
        grid.getDataCommunicator().getKeyMapper().refresh(secondFetched);

        assertThat(DragAndDropSupport.getSelectedItemsAsLastFetched(grid)).containsExactly(first, secondFetched)
                .extracting(ProxyDistributionSet::getIsValid).containsExactly(true, false);
    }

    private static Grid<ProxyDistributionSet> gridWithSelected(final ProxyDistributionSet selected) {
        final Grid<ProxyDistributionSet> grid = new Grid<>();
        grid.setItems(selected);
        grid.setSelectionMode(SelectionMode.MULTI);
        grid.select(selected);
        return grid;
    }

    private static ProxyDistributionSet distributionSet(final Long id, final boolean valid) {
        final ProxyDistributionSet ds = new ProxyDistributionSet();
        ds.setId(id);
        ds.setNameVersion("Seed Apps:1.0.0");
        ds.setIsValid(valid);
        return ds;
    }
}
