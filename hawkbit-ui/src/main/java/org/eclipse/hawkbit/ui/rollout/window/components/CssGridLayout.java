/**
 * Copyright (c) 2023 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.hawkbit.ui.rollout.window.components;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.orderedlayout.FlexComponent.Alignment;

/**
 * The part of Vaadin 8's {@code GridLayout} that the rollout window uses, on a
 * CSS grid: components placed in cells or cell areas by column and row index,
 * rows inserted and removed with the same rules (areas below move, areas
 * across stretch or shrink, components in a removed row are removed), column
 * expand ratios, spacing and margin. Flow has no grid layout component.
 */
public class CssGridLayout extends Div {
    private static final long serialVersionUID = 1L;

    private static final String SPACING = "var(--lumo-space-m, 1rem)";

    private final Map<Component, int[]> areas = new LinkedHashMap<>();
    private final Map<Integer, Float> columnExpandRatios = new HashMap<>();

    private int columns = 1;
    private int rows = 1;

    /**
     * Constructor: one column and one row, no spacing, no margin, components
     * at the top left of their cells (Vaadin 8 defaults).
     */
    public CssGridLayout() {
        getStyle().set("display", "grid");
        getStyle().set("align-items", "start");
        getStyle().set("justify-items", "start");
        updateColumnTemplate();
    }

    /**
     * @param columns
     *            number of columns
     */
    public void setColumns(final int columns) {
        this.columns = columns;
        updateColumnTemplate();
    }

    /**
     * @return number of columns
     */
    public int getColumns() {
        return columns;
    }

    /**
     * @param rows
     *            number of rows
     */
    public void setRows(final int rows) {
        this.rows = rows;
    }

    /**
     * @return number of rows
     */
    public int getRows() {
        return rows;
    }

    /**
     * @param spacing
     *            space between the cells
     */
    public void setSpacing(final boolean spacing) {
        if (spacing) {
            getStyle().set("gap", SPACING);
        } else {
            getStyle().remove("gap");
        }
    }

    /**
     * @param margin
     *            space around the cells (Vaadin 8 {@code setMargin})
     */
    public void setPadding(final boolean margin) {
        if (margin) {
            getStyle().set("padding", SPACING);
        } else {
            getStyle().remove("padding");
        }
    }

    /**
     * Columns with a ratio share the free width; the others take the width of
     * their content.
     *
     * @param columnIndex
     *            column index
     * @param ratio
     *            expand ratio
     */
    public void setColumnExpandRatio(final int columnIndex, final float ratio) {
        columnExpandRatios.put(columnIndex, ratio);
        updateColumnTemplate();
    }

    /**
     * Adds a component to one cell.
     *
     * @param component
     *            the component
     * @param column
     *            column index
     * @param row
     *            row index
     */
    public void addComponent(final Component component, final int column, final int row) {
        addComponent(component, column, row, column, row);
    }

    /**
     * Adds a component to an area of cells.
     *
     * @param component
     *            the component
     * @param column1
     *            first column
     * @param row1
     *            first row
     * @param column2
     *            last column
     * @param row2
     *            last row
     */
    public void addComponent(final Component component, final int column1, final int row1, final int column2,
            final int row2) {
        if (column2 >= columns || row2 >= rows || column1 < 0 || row1 < 0 || column1 > column2 || row1 > row2) {
            throw new IllegalArgumentException("Area " + column1 + "," + row1 + " - " + column2 + "," + row2
                    + " is outside of the grid (" + columns + " columns, " + rows + " rows)");
        }
        areas.put(component, new int[] { column1, row1, column2, row2 });
        add(component);
        updatePosition(component);
    }

    /**
     * Inserts an empty row: areas below it move down, areas across it grow.
     *
     * @param row
     *            index of the new row
     */
    public void insertRow(final int row) {
        if (row > rows) {
            throw new IllegalArgumentException("Cannot insert row at " + row + " in a grid of " + rows + " rows");
        }
        areas.forEach((component, area) -> {
            if (area[3] >= row) {
                area[3]++;
                if (area[1] >= row) {
                    area[1]++;
                }
                updatePosition(component);
            }
        });
        rows++;
    }

    /**
     * Removes a row: components in it are removed, areas below it move up,
     * areas across it shrink.
     *
     * @param row
     *            index of the row
     */
    public void removeRow(final int row) {
        if (row >= rows) {
            throw new IllegalArgumentException("Cannot remove row " + row + " from a grid of " + rows + " rows");
        }
        final List<Component> inRow = areas.entrySet().stream()
                .filter(entry -> entry.getValue()[1] <= row && row <= entry.getValue()[3]).map(Map.Entry::getKey)
                .collect(Collectors.toCollection(ArrayList::new));
        inRow.forEach(this::removeComponent);

        areas.forEach((component, area) -> {
            if (area[3] >= row) {
                area[3]--;
                if (area[1] > row) {
                    area[1]--;
                }
                updatePosition(component);
            }
        });
        if (rows > 1) {
            rows--;
        }
    }

    /**
     * Removes a component from the grid.
     *
     * @param component
     *            the component
     */
    public void removeComponent(final Component component) {
        areas.remove(component);
        remove(component);
    }

    /**
     * Vertical alignment of a component in its cell (Vaadin 8
     * {@code setComponentAlignment}; horizontally components stay left).
     *
     * @param alignment
     *            vertical alignment
     * @param component
     *            the component
     */
    public void setVerticalComponentAlignment(final Alignment alignment, final Component component) {
        final String alignSelf;
        switch (alignment) {
        case CENTER:
            alignSelf = "center";
            break;
        case END:
            alignSelf = "end";
            break;
        case STRETCH:
            alignSelf = "stretch";
            break;
        case BASELINE:
            alignSelf = "baseline";
            break;
        case START:
            alignSelf = "start";
            break;
        default:
            alignSelf = null;
        }
        component.getElement().getStyle().set("align-self", alignSelf);
    }

    private void updatePosition(final Component component) {
        final int[] area = areas.get(component);
        component.getElement().getStyle().set("grid-column", (area[0] + 1) + " / " + (area[2] + 2));
        component.getElement().getStyle().set("grid-row", (area[1] + 1) + " / " + (area[3] + 2));
    }

    private void updateColumnTemplate() {
        getStyle().set("grid-template-columns", IntStream.range(0, columns).mapToObj(column -> {
            final Float ratio = columnExpandRatios.get(column);
            return ratio != null && ratio > 0 ? ratio + "fr" : "auto";
        }).collect(Collectors.joining(" ")));
    }
}
