/**
 * Copyright (c) 2023 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.hawkbit.ui.management.dstable;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.eclipse.hawkbit.ui.common.data.proxies.ProxyDistributionSet;
import org.junit.jupiter.api.Test;

import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.Grid.SelectionMode;

import io.qameta.allure.Feature;
import io.qameta.allure.Story;

@Feature("Unit Tests - Management UI")
@Story("Invalidating a selection leaves out the sets another user already invalidated")
class InvalidateDistributionSetSupportTest {

    @Test
    void setInvalidatedAfterSelectionIsLeftOut() {
        final ProxyDistributionSet a = distributionSet(1L, true);
        final ProxyDistributionSet b = distributionSet(2L, true);
        final Grid<ProxyDistributionSet> grid = new Grid<>();
        grid.setItems(a, b);
        grid.setSelectionMode(SelectionMode.MULTI);
        grid.select(a);
        grid.select(b);

        // B was invalidated by someone else; the grid fetches the row again
        final ProxyDistributionSet bFetched = distributionSet(2L, false);
        grid.getDataCommunicator().getKeyMapper().key(bFetched);
        grid.getDataCommunicator().getKeyMapper().refresh(bFetched);

        final List<ProxyDistributionSet> forInvalidation = InvalidateDistributionSetSupport
                .getDistributionSetsForInvalidation(grid, a);

        assertThat(forInvalidation).containsExactly(a);
    }

    @Test
    void clickedSetOutsideTheSelectionIsInvalidatedAlone() {
        final ProxyDistributionSet a = distributionSet(1L, true);
        final ProxyDistributionSet b = distributionSet(2L, true);
        final Grid<ProxyDistributionSet> grid = new Grid<>();
        grid.setItems(a, b);
        grid.setSelectionMode(SelectionMode.MULTI);
        grid.select(a);

        assertThat(InvalidateDistributionSetSupport.getDistributionSetsForInvalidation(grid, b)).containsExactly(b);
    }

    private static ProxyDistributionSet distributionSet(final Long id, final boolean valid) {
        final ProxyDistributionSet ds = new ProxyDistributionSet();
        ds.setId(id);
        ds.setNameVersion("Seed Apps:" + id);
        ds.setIsValid(valid);
        return ds;
    }
}
