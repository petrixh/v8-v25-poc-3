/**
 * Copyright (c) 2023 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.hawkbit.ui.common.detailslayout;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.eclipse.hawkbit.repository.DistributionSetManagement;
import org.eclipse.hawkbit.ui.common.CommonUiDependencies;
import org.eclipse.hawkbit.ui.common.data.providers.DsMetaDataDataProvider;
import org.eclipse.hawkbit.ui.utils.VaadinMessageSource;
import org.junit.jupiter.api.Test;

import io.qameta.allure.Feature;
import io.qameta.allure.Story;

/**
 * The list of the metadata window (distribution set, software module, target)
 * keeps the grid's default height, as in Vaadin 8, instead of a relative
 * height that collapses to one row in the window (2B-10).
 */
@Feature("Unit Tests - Management UI")
@Story("Users see the metadata entries of an entity in its metadata window")
class MetaDataWindowGridTest {

    @Test
    void metadataListHasNoRelativeHeight() {
        final CommonUiDependencies uiDependencies = mock(CommonUiDependencies.class);
        when(uiDependencies.getI18n()).thenReturn(mock(VaadinMessageSource.class));
        final DsMetaDataDataProvider dataProvider = new DsMetaDataDataProvider(
                mock(DistributionSetManagement.class));

        final MetaDataWindowGrid<Long> grid = new MetaDataWindowGrid<>(uiDependencies, dataProvider, () -> true,
                items -> true);

        assertThat(grid.getHeight()).isNull();
        assertThat(grid.getWidth()).isEqualTo("100%");
    }
}
