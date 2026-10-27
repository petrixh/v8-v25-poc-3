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

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import com.vaadin.flow.component.ComponentUtil;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.contextmenu.ContextMenu;
import com.vaadin.flow.component.contextmenu.MenuItem;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.Grid.Column;
import com.vaadin.flow.component.icon.VaadinIcon;

/**
 * Vaadin 8 hidable grid columns ({@code Column.setHidable(true)}) on Flow:
 * a toggle button at the end of the grid header opens a menu with one
 * checkable item per hidable column; unchecking an item hides the column,
 * checking it shows it again. Flow's Grid has no such menu, so it is built
 * here with public API only: an extra narrow column frozen to the end holds
 * the button in its header (where Vaadin 8 put its toggle), and a
 * {@link ContextMenu} opens on a click on the button.
 * <p>
 * Use it after the grid's columns are added (the toggle column is then moved
 * to the end), in place of {@code setHidable(true)}:
 *
 * <pre>
 * ColumnToggleMenu.setHidable(column); // caption: the column's header text
 * ColumnToggleMenu.setHidable(column, caption); // Vaadin 8 setHidingToggleCaption
 * ColumnToggleMenu.setAllHidable(grid); // getColumns().forEach(c -> c.setHidable(true))
 * </pre>
 *
 * {@code setHidable(false)} needs no call (not hidable is the default).
 * Hidden columns ({@code setVisible(false)}) show unchecked. When the grid's
 * columns are removed and added again (maximize/minimize), the hidable columns
 * are registered again by the code that adds them; the toggle column is
 * recreated as needed.
 *
 * @param <T>
 *            the grid's item type
 */
public final class ColumnToggleMenu<T> implements Serializable {
    private static final long serialVersionUID = 1L;

    /** Key of the toggle column; it is not a data column. */
    public static final String TOGGLE_COLUMN_KEY = "column-toggle";

    private static final String TOGGLE_STYLE = "column-toggle-button";

    private final Grid<T> grid;
    private final ContextMenu menu = new ContextMenu();
    private final LinkedHashMap<Column<T>, String> hidableColumns = new LinkedHashMap<>();
    private final LinkedHashMap<Column<T>, MenuItem> items = new LinkedHashMap<>();
    private Column<T> toggleColumn;

    private ColumnToggleMenu(final Grid<T> grid) {
        this.grid = grid;
        menu.setOpenOnClick(true);
        // columns may have been shown or hidden by code since the last open
        menu.addOpenedChangeListener(event -> {
            if (event.isOpened()) {
                syncCheckedState();
            }
        });
    }

    /**
     * Makes a column hidable by the user, with its header text as the caption
     * in the menu.
     *
     * @param column
     *            the column, already added to its grid
     * @return the column
     */
    public static <T> Column<T> setHidable(final Column<T> column) {
        return setHidable(column, column.getHeaderText());
    }

    /**
     * Makes a column hidable by the user.
     *
     * @param column
     *            the column, already added to its grid
     * @param toggleCaption
     *            the caption of the column in the menu (Vaadin 8
     *            setHidingToggleCaption)
     * @return the column
     */
    @SuppressWarnings("unchecked")
    public static <T> Column<T> setHidable(final Column<T> column, final String toggleCaption) {
        final Grid<T> grid = (Grid<T>) column.getGrid();
        if (TOGGLE_COLUMN_KEY.equals(column.getKey())) {
            return column;
        }
        forGrid(grid).register(column, toggleCaption);
        return column;
    }

    /**
     * Makes every column of the grid hidable by the user (Vaadin 8:
     * {@code getColumns().forEach(column -> column.setHidable(true))}).
     *
     * @param grid
     *            the grid, with its columns added
     */
    public static <T> void setAllHidable(final Grid<T> grid) {
        new ArrayList<>(grid.getColumns()).forEach(ColumnToggleMenu::setHidable);
    }

    @SuppressWarnings("unchecked")
    private static <T> ColumnToggleMenu<T> forGrid(final Grid<T> grid) {
        ColumnToggleMenu<T> toggleMenu = ComponentUtil.getData(grid, ColumnToggleMenu.class);
        if (toggleMenu == null) {
            toggleMenu = new ColumnToggleMenu<>(grid);
            ComponentUtil.setData(grid, ColumnToggleMenu.class, toggleMenu);
        }
        return toggleMenu;
    }

    private void register(final Column<T> column, final String toggleCaption) {
        final List<Column<T>> columns = grid.getColumns();
        // forget columns that were removed from the grid since
        hidableColumns.keySet().removeIf(registered -> !columns.contains(registered));
        hidableColumns.put(column, toggleCaption == null ? "" : toggleCaption);

        if (toggleColumn == null || !columns.contains(toggleColumn)) {
            addToggleColumn();
        }
        moveToggleColumnLast();
        rebuildItems();
    }

    private void addToggleColumn() {
        final Button toggleButton = new Button(VaadinIcon.MENU.create());
        toggleButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE, ButtonVariant.LUMO_ICON);
        toggleButton.addClassName(TOGGLE_STYLE);
        menu.setTarget(toggleButton);

        toggleColumn = grid.addColumn(item -> "");
        toggleColumn.setKey(TOGGLE_COLUMN_KEY);
        toggleColumn.setHeader(toggleButton);
        toggleColumn.setWidth("40px");
        toggleColumn.setFlexGrow(0);
        toggleColumn.setSortable(false);
        toggleColumn.setResizable(false);
        toggleColumn.setFrozenToEnd(true);
    }

    private void moveToggleColumnLast() {
        final List<Column<T>> order = new ArrayList<>(grid.getColumns());
        if (order.get(order.size() - 1) != toggleColumn) {
            order.remove(toggleColumn);
            order.add(toggleColumn);
            grid.setColumnOrder(order);
        }
    }

    private void rebuildItems() {
        menu.removeAll();
        items.clear();
        hidableColumns.forEach((column, caption) -> {
            final MenuItem item = menu.addItem(caption, event -> column.setVisible(event.getSource().isChecked()));
            item.setCheckable(true);
            item.setChecked(column.isVisible());
            item.setKeepOpen(true);
            items.put(column, item);
        });
    }

    private void syncCheckedState() {
        items.forEach((column, item) -> item.setChecked(column.isVisible()));
    }
}
