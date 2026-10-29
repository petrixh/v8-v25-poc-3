/**
 * Copyright (c) 2023 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.hawkbit.ui.common.data.providers;

import static org.assertj.core.api.Assertions.assertThat;


import org.eclipse.hawkbit.repository.model.MetaData;
import org.eclipse.hawkbit.ui.common.data.proxies.ProxyMetaData;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;

import com.vaadin.flow.component.grid.Grid;

import io.qameta.allure.Feature;
import io.qameta.allure.Story;

/**
 * A metadata row keeps its identity when the list is reloaded: after a save the
 * list is refreshed with newly mapped items, and the saved row must still be the
 * selected one (Vaadin 8: items were compared by entity id and key).
 */
@Feature("Unit Tests - Management UI")
@Story("Users see the saved metadata row highlighted in the metadata list")
class AbstractMetaDataDataProviderTest {

    private final AbstractMetaDataDataProvider<MetaData, String> dataProvider = new AbstractMetaDataDataProvider<>() {
        private static final long serialVersionUID = 1L;

        @Override
        protected Slice<MetaData> loadBackendEntities(final PageRequest pageRequest, final String filter) {
            throw new UnsupportedOperationException("not needed");
        }

        @Override
        protected long sizeInBackEnd(final PageRequest pageRequest, final String filter) {
            return 0;
        }
    };

    private static ProxyMetaData metaData(final Long entityId, final String key) {
        final ProxyMetaData metaData = new ProxyMetaData();
        metaData.setEntityId(entityId);
        metaData.setKey(key);
        return metaData;
    }

    @Test
    void sameEntityAndKeyGiveTheSameIdForDifferentInstances() {
        assertThat(dataProvider.getId(metaData(7L, "key-4"))).isEqualTo(dataProvider.getId(metaData(7L, "key-4")));
        assertThat(dataProvider.getId(metaData(7L, "key-4"))).isNotEqualTo(dataProvider.getId(metaData(7L, "key-3")));
        assertThat(dataProvider.getId(metaData(7L, "key-4"))).isNotEqualTo(dataProvider.getId(metaData(8L, "key-4")));
    }

    @Test
    void selectionSurvivesReloadedItems() {
        final Grid<ProxyMetaData> grid = new Grid<>();
        grid.setDataProvider(dataProvider.withConfigurableFilter());
        grid.setSelectionMode(Grid.SelectionMode.SINGLE);

        // the saved item, then the list is reloaded and maps the same row to a new instance
        grid.select(metaData(7L, "key-4"));

        final ProxyMetaData reloaded = metaData(7L, "key-4");
        assertThat(grid.getSelectionModel().isSelected(reloaded)).isTrue();
        assertThat(grid.getSelectionModel().isSelected(metaData(7L, "key-3"))).isFalse();
    }
}
