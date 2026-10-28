/**
 * Copyright (c) 2020 Bosch.IO GmbH and others
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.hawkbit.ui.common.grid.selection;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.stream.Collectors;

import org.eclipse.hawkbit.ui.utils.SPUIStyleDefinitions;
import org.eclipse.hawkbit.ui.utils.UINotification;
import org.eclipse.hawkbit.ui.utils.VaadinMessageSource;

import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.KeyModifier;
import com.vaadin.flow.component.Shortcuts;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.Grid.SelectionMode;
import com.vaadin.flow.component.grid.GridMultiSelectionModel;
import com.vaadin.flow.component.grid.ItemClickEvent;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.data.provider.DataProvider;
import com.vaadin.flow.data.provider.Query;
import com.vaadin.flow.data.selection.MultiSelect;
import com.vaadin.flow.dom.DomEvent;
import com.vaadin.flow.shared.Registration;

/**
 * 
 * Allows selecting items of a multi-selection grid by clicking instead of
 * using the checkboxes. Also allows using CTRL and SHIFT to select multiple
 * items or a range of items.
 *
 * Vaadin 25 (add-on A4): Vaadin 8 extended the grid's multi-selection model
 * and did the clicks and keys in a GWT connector. Here the grid uses Flow's
 * own multi-selection, and the same clicks and keys are handled on the
 * server: click selects one row, CTRL/CMD+click toggles a row, SHIFT+click
 * selects the range from the last clicked row (fetched from the data
 * provider), CTRL/CMD+A selects all, Up/Down moves the single selection and
 * SHIFT+Up/Down extends the range; at most {@link #MAX_SELECTION_LIMIT} rows,
 * with the same warning. Flow keeps its checkbox column visible (hiding it
 * needs non-public API).
 *
 * @param <T>
 *            Item type
 */
public class RangeSelectionModel<T> implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * Maximum number of selected items (Vaadin 8: RangeSelectionState)
     */
    public static final int MAX_SELECTION_LIMIT = 1000;

    // index of the row from the grid web component's public event context
    private static final String ROW_INDEX = "element.getEventContext(event).index";
    private static final String ROW_SECTION = "element.getEventContext(event).section";

    private final VaadinMessageSource i18n;

    private final List<Registration> registrations = new ArrayList<>();

    private Grid<T> grid;
    private int focusedRowIndex = -1;
    private int previousRowIndex;

    public RangeSelectionModel(final VaadinMessageSource i18n) {
        this.i18n = i18n;
    }

    /**
     * Sets the grid to multi-selection and adds the range selection to it.
     *
     * @param grid
     *            the grid
     * @return the grid's multi-selection model
     */
    public GridMultiSelectionModel<T> extend(final Grid<T> grid) {
        remove();
        this.grid = grid;

        // AbstractGrid sets multi-selection before it calls this
        final GridMultiSelectionModel<T> model;
        if (grid.getSelectionModel() instanceof final GridMultiSelectionModel<T> multi) {
            model = multi;
        } else {
            model = (GridMultiSelectionModel<T>) grid.setSelectionMode(SelectionMode.MULTI);
        }

        registrations.add(grid.getElement().addEventListener("cell-focus", this::onCellFocus)
                .addEventData(ROW_INDEX).addEventData(ROW_SECTION));
        registrations.add(grid.addItemClickListener(this::onClick));
        registrations.add(grid.getElement().addEventListener("keyup", this::onKeyUp).addEventData("event.shiftKey")
                .setFilter("event.key === 'ArrowUp' || event.key === 'ArrowDown'"));
        // CTRL+A and CMD+A: consumed, so the browser does not select the page
        registrations.add(Shortcuts.addShortcutListener(grid, this::selectAll, Key.KEY_A, KeyModifier.CONTROL)
                .listenOn(grid));
        registrations.add(
                Shortcuts.addShortcutListener(grid, this::selectAll, Key.KEY_A, KeyModifier.META).listenOn(grid));

        return model;
    }

    /**
     * Removes the range selection from the grid (the grid's selection mode is
     * left as it is).
     */
    public void remove() {
        registrations.forEach(Registration::remove);
        registrations.clear();
    }

    private MultiSelect<Grid<T>, T> getSelection() {
        return grid.asMultiSelect();
    }

    private void onCellFocus(final DomEvent event) {
        if ("body".equals(event.getEventData().path(ROW_SECTION).asString(null))
                && event.getEventData().path(ROW_INDEX).isNumber()) {
            focusedRowIndex = event.getEventData().path(ROW_INDEX).asInt();
        }
    }

    private void onClick(final ItemClickEvent<T> event) {
        final T item = event.getItem();
        if (item == null || event.getColumn() == null) {
            // no row, or the checkbox column (Flow's own selection)
            return;
        }
        final int currentRowIndex = focusedRowIndex;
        final boolean preserveSelection = event.isCtrlKey() || event.isMetaKey();

        if (event.isShiftKey() && !preserveSelection && currentRowIndex >= 0) {
            selectRange(previousRowIndex, currentRowIndex);
            return;
        }

        previousRowIndex = currentRowIndex;

        if (preserveSelection) {
            adaptSelectionByItem(item);
            return;
        }

        selectSingleItemOnly(item);
    }

    private void adaptSelectionByItem(final T item) {
        if (getSelection().isSelected(item)) {
            getSelection().deselect(item);
            return;
        }

        if (isMaxSelectionLimitReached()) {
            showMaxSelectionLimitWarning();
            return;
        }

        getSelection().select(item);
    }

    private boolean isMaxSelectionLimitReached() {
        return getSelection().getSelectedItems().size() + 1 > MAX_SELECTION_LIMIT;
    }

    private void selectSingleItemOnly(final T item) {
        getSelection().deselectAll();
        getSelection().select(item);
    }

    private void onKeyUp(final DomEvent event) {
        final int currentRowIndex = focusedRowIndex;
        if (currentRowIndex < 0) {
            return;
        }

        if (event.getEventData().path("event.shiftKey").asBoolean(false)) {
            selectRange(previousRowIndex, currentRowIndex);
            return;
        }

        previousRowIndex = currentRowIndex;

        final T item = grid.getDataCommunicator().getItem(currentRowIndex);
        if (item != null) {
            selectSingleItemOnly(item);
        }
    }

    private void selectRange(final int startIndex, final int endIndex) {
        getSelection().deselectAll();
        onSelectRange(startIndex, endIndex);
    }

    private void selectAll() {
        getSelection().deselectAll();
        onSelectRange(0, grid.getDataCommunicator().getDataProviderSize());
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private void onSelectRange(final int startIndex, final int endIndex) {
        int offset = Math.min(startIndex, endIndex);
        int limit = Math.abs(startIndex - endIndex) + 1;

        if (limit > MAX_SELECTION_LIMIT) {
            showMaxSelectionLimitWarning();

            limit = MAX_SELECTION_LIMIT;

            if (startIndex > endIndex) {
                offset = startIndex - MAX_SELECTION_LIMIT + 1;
            }
        }

        // same filter and sort order as the grid shows
        final Query<T, Object> query = grid.getDataCommunicator().buildQuery(offset, limit);
        final LinkedHashSet<T> addedItems = ((DataProvider<T, Object>) grid.getDataProvider()).fetch(query)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        getSelection().updateSelection(addedItems, Collections.emptySet());
    }

    private void showMaxSelectionLimitWarning() {
        UINotification.showNotification(SPUIStyleDefinitions.SP_NOTIFICATION_WARNING_MESSAGE_STYLE,
                i18n.getMessage("action.grid.selection.limit", MAX_SELECTION_LIMIT), VaadinIcon.WARNING);
    }
}
