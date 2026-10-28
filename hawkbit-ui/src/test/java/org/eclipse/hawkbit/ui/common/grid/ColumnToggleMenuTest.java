/**
 * Copyright (c) 2023 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.hawkbit.ui.common.grid;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;

import org.eclipse.hawkbit.ui.common.builder.GridComponentBuilder;
import org.junit.jupiter.api.Test;

import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.Grid.Column;
import com.vaadin.flow.component.html.Span;

import io.qameta.allure.Feature;
import io.qameta.allure.Story;

/**
 * The column toggle menu lists every hidable column by its caption, also when
 * the column's header shows a component or was joined (Vaadin 8: the column
 * caption, not the header cell), and its toggle column stays the last column.
 */
@Feature("Unit Tests - Management UI")
@Story("Users hide grid columns through the column toggle menu")
class ColumnToggleMenuTest {

    private final Grid<String> grid = new Grid<>();

    @Test
    void joinedColumnsKeepTheirCaptionsInTheMenu() {
        grid.addColumn(item -> item).setHeader("File name");
        final Column<String> download = GridComponentBuilder.addIconColumn(grid, item -> new Span(), "download",
                "Download");
        final Column<String> delete = GridComponentBuilder.addIconColumn(grid, item -> new Span(), "delete",
                "Delete");

        GridComponentBuilder.joinToIconColumn(grid.getHeaderRows().get(0), "Actions",
                Arrays.<Column<?>> asList(download, delete));
        ColumnToggleMenu.setAllHidable(grid);

        // the header shows the joined caption component, not the column caption
        assertThat(download.getHeaderComponent()).isNotNull();
        assertThat(download.getHeaderText()).isNull();
        assertThat(delete.getHeaderText()).isEmpty();
        assertThat(ColumnToggleMenu.getToggleCaptions(grid)).containsExactly("File name", "Download", "Delete");
        assertToggleColumnLast();
    }

    @Test
    void headerComponentUsesTheToggleCaption() {
        final Column<String> name = grid.addColumn(item -> item).setHeader("Name");
        final Column<String> status = grid.addColumn(item -> item).setHeader(new Span("Status"));
        ColumnToggleMenu.setToggleCaption(status, "Status");

        ColumnToggleMenu.setHidable(name);
        ColumnToggleMenu.setHidable(status);

        assertThat(ColumnToggleMenu.getToggleCaptions(grid)).containsExactly("Name", "Status");
        assertToggleColumnLast();
    }

    @Test
    void explicitCaptionAndHeaderTextAsBefore() {
        final Column<String> description = grid.addColumn(item -> item).setHeader("Description");
        final Column<String> value = grid.addColumn(item -> item).setHeader("Value");

        ColumnToggleMenu.setHidable(description, "Long description");
        ColumnToggleMenu.setHidable(value).setVisible(false);

        assertThat(ColumnToggleMenu.getToggleCaptions(grid)).containsExactly("Long description", "Value");
        assertThat(value.isVisible()).isFalse();
        assertToggleColumnLast();
    }

    private void assertToggleColumnLast() {
        final List<Column<String>> columns = grid.getColumns();
        assertThat(columns.get(columns.size() - 1).getKey()).isEqualTo(ColumnToggleMenu.TOGGLE_COLUMN_KEY);
    }
}
