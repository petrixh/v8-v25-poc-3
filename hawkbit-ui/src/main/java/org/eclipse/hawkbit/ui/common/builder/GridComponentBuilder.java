/**
 * Copyright (c) 2020 Bosch.IO GmbH and others
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.hawkbit.ui.common.builder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.eclipse.hawkbit.ui.common.data.proxies.ProxyIdentifiableEntity;
import org.eclipse.hawkbit.ui.common.data.proxies.ProxyNamedEntity;
import org.eclipse.hawkbit.ui.common.data.proxies.ProxyTarget;
import org.eclipse.hawkbit.ui.common.grid.support.DeleteSupport;
import org.eclipse.hawkbit.ui.utils.StringHtmlEncoder;
import org.eclipse.hawkbit.ui.utils.SPUIStyleDefinitions;
import org.eclipse.hawkbit.ui.utils.UIMessageIdProvider;
import org.eclipse.hawkbit.ui.utils.VaadinMessageSource;

import com.cronutils.utils.StringUtils;
import com.vaadin.flow.component.ClickEvent;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.ComponentEventListener;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.Grid.Column;
import com.vaadin.flow.component.grid.HeaderRow;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.function.SerializableFunction;
import com.vaadin.flow.function.ValueProvider;

/**
 * Builder class for grid components
 */
public final class GridComponentBuilder {
    public static final double DEFAULT_MIN_WIDTH = 100D;

    public static final String CREATED_BY_ID = "createdBy";
    public static final String CREATED_DATE_ID = "createdDate";
    public static final String MODIFIED_BY_ID = "modifiedBy";
    public static final String MODIFIED_DATE_ID = "modifiedDate";

    public static final String CREATED_BY_PROPERTY_NAME = "createdBy";
    public static final String CREATED_AT_PROPERTY_NAME = "createdAt";
    public static final String LAST_MODIFIED_BY_PROPERTY_NAME = "lastModifiedBy";
    public static final String LAST_MODIFIED_AT_PROPERTY_NAME = "lastModifiedAt";
    public static final String VERSION_PROPERTY_NAME = "version";
    public static final String NAME_PROPERTY_NAME = "name";
    public static final String CONTROLLER_ID_PROPERTY_NAME = "controllerId";

    private GridComponentBuilder() {
    }

    /**
     * Create a {@link Button} with link optic
     *
     * @param idSuffix
     *            suffix to build the button ID
     * @param idPrefix
     *            prefix to build the button ID
     * @param caption
     *            button caption
     * @param enabled
     *            is button enabled
     * @param clickListener
     *            execute on button click (null for none)
     * @return the button
     */
    public static Button buildLink(final String idSuffix, final String idPrefix, final String caption,
            final boolean enabled, final ComponentEventListener<ClickEvent<Button>> clickListener) {
        final Button link = new Button();
        final String id = new StringBuilder(idPrefix).append('.').append(idSuffix).toString();

        link.setText(caption);
        link.setEnabled(enabled);
        link.setId(id);
        link.addClassNames("borderless", "small", "on-focus-no-border", "link");
        if (clickListener != null) {
            link.addClickListener(clickListener);
        }
        link.setVisible(!StringUtils.isEmpty(caption));
        return link;
    }

    /**
     * Create a {@link Button} with link optic
     *
     * @param entity
     *            to build the button ID
     * @param idPrefix
     *            prefix to build the button ID
     * @param caption
     *            button caption
     * @param enabled
     *            is button enabled
     * @param clickListener
     *            execute on button click (null for none)
     * @return the button
     */
    public static <E extends ProxyNamedEntity> Button buildLink(final E entity, final String idPrefix,
            final String caption, final boolean enabled, final ComponentEventListener<ClickEvent<Button>> clickListener) {
        final String idSuffix = StringHtmlEncoder.encode(entity.getName());
        return buildLink(idSuffix, idPrefix, caption, enabled, clickListener);
    }

    /**
     * Add name column to grid. The column is set to sortable which implies a
     * JPA field "name" that is used for sorting.
     *
     * @param <E>
     *            entity type of the grid
     * @param grid
     *            to add the column to
     * @param i18n
     *            message source for internationalization
     * @param columnId
     *            column ID
     * @return the created column
     */
    public static <E extends ProxyNamedEntity> Column<E> addNameColumn(final Grid<E> grid,
            final VaadinMessageSource i18n, final String columnId) {
        final Column<E> nameColumn = addColumn(i18n, grid, E::getName, "header.name", columnId,
                DEFAULT_MIN_WIDTH);
        setColumnSortable(nameColumn, NAME_PROPERTY_NAME);
        return nameColumn;
    }

    /**
     * Add controllerId column to grid.  The column is set to sortable which implies a
     * JPA field "controllerId" that is used for sorting.
     *
     * @param grid
     *            to add the column to
     * @param i18n
     *            message source for internationalization
     * @param columnId
     *            column ID
     * @return the created column
     */
    public static Column<ProxyTarget> addControllerIdColumn(final Grid<ProxyTarget> grid,
            final VaadinMessageSource i18n, final String columnId) {
        final Column<ProxyTarget> column = addComponentColumn(grid,
                t -> buildControllerIdLink(t, columnId)).setKey(columnId)
                        .setHeader(i18n.getMessage("header.controllerId"))
                        .setWidth(toPixels(DEFAULT_MIN_WIDTH));
        setColumnSortable(column, CONTROLLER_ID_PROPERTY_NAME);
        return column;
    }

    private static Button buildControllerIdLink(final ProxyTarget target, final String linkIdPrefix) {
        final String idSuffix = StringHtmlEncoder.encode(target.getControllerId());
        return buildLink(idSuffix, linkIdPrefix, target.getControllerId(), true,
                clickEvent -> UI.getCurrent().navigate("deployment/target=" + target.getControllerId()));
    }

    /**
     * Add description column to grid
     *
     * @param <E>
     *            entity type of the grid
     * @param grid
     *            to add the column to
     * @param i18n
     *            message source for internationalization
     * @param columnId
     *            column ID
     * @return the created column
     */
    public static <E extends ProxyNamedEntity> Column<E> addDescriptionColumn(final Grid<E> grid,
            final VaadinMessageSource i18n, final String columnId) {
        return addColumn(i18n, grid, E::getDescription, "header.description", columnId, DEFAULT_MIN_WIDTH);
    }

    /**
     * Add "created by", "created at", "modified by" and "modified at" column
     *
     * @param <E>
     *            entity type of the grid
     * @param grid
     *            to add the columns to
     * @param i18n
     *            message source for internationalization
     * @return the created columns
     */
    public static <E extends ProxyNamedEntity> List<Column<E>> addCreatedAndModifiedColumns(final Grid<E> grid,
            final VaadinMessageSource i18n) {
        final List<Column<E>> columns = new ArrayList<>();

        final Column<E> createdByColumn = addColumn(i18n, grid, E::getCreatedBy, "header.createdBy",
                CREATED_BY_ID, DEFAULT_MIN_WIDTH);
        setColumnSortable(createdByColumn, CREATED_BY_PROPERTY_NAME);
        columns.add(createdByColumn);

        final Column<E> createdDate = addColumn(i18n, grid, E::getCreatedDate, "header.createdDate", CREATED_DATE_ID, DEFAULT_MIN_WIDTH);
        setColumnSortable(createdDate, CREATED_AT_PROPERTY_NAME);
        columns.add(createdDate);

        final Column<E> modifiedBy = addColumn(i18n, grid, E::getLastModifiedBy, "header.modifiedBy", MODIFIED_BY_ID, DEFAULT_MIN_WIDTH);
        setColumnSortable(modifiedBy, LAST_MODIFIED_BY_PROPERTY_NAME);
        columns.add(modifiedBy);

        final Column<E> modifiedDate = addColumn(i18n, grid, E::getModifiedDate, "header.modifiedDate", MODIFIED_DATE_ID, DEFAULT_MIN_WIDTH);
        setColumnSortable(modifiedDate, LAST_MODIFIED_AT_PROPERTY_NAME);
        columns.add(modifiedDate);

        return columns;
    }

    /**
     * Add version column to grid
     *
     * @param <E>
     *            entity type of the grid
     * @param grid
     *            to add the column to
     * @param i18n
     *            message source for internationalization
     * @param valueProvider
     *            to get the version of the entity
     * @param columnId
     *            column ID
     * @return the created column
     */
    public static <E> Column<E> addVersionColumn(final Grid<E> grid, final VaadinMessageSource i18n,
            final ValueProvider<E, String> valueProvider, final String columnId) {
        final Column<E> column = addColumn(i18n, grid, valueProvider, "header.version", columnId,
                DEFAULT_MIN_WIDTH);
        setColumnSortable(column, VERSION_PROPERTY_NAME);
        return column;
    }

    private static <E, T> Column<E> addColumn(final VaadinMessageSource i18n, final Grid<E> grid,
            final ValueProvider<E, T> valueProvider, final String caption, final String columnID,
            final double minWidth) {
        final Column<E> col = addColumn(grid, valueProvider).setHeader(i18n.getMessage(caption))
                .setWidth(toPixels(minWidth));
        if (columnID != null) {
            col.setKey(columnID);
        }
        return col;

    }

    /**
     * Add column to grid with the standard settings
     *
     * @param <E>
     *            entity type of the grid
     * @param grid
     *            to add the column to
     * @param valueProvider
     *            providing the content
     * @return the created column
     */
    public static <E, T> Column<E> addColumn(final Grid<E> grid, final ValueProvider<E, T> valueProvider) {
        return addColumn(grid, valueProvider, null);
    }

    /**
     * Add column to grid with the standard settings
     *
     * @param <E>
     *            entity type of the grid
     * @param grid
     *            to add the column to
     * @param valueProvider
     *            providing the content
     * @param styleGenerator
     *            providing additional styles
     * @return the created column
     */
    public static <E, T> Column<E> addColumn(final Grid<E> grid, final ValueProvider<E, T> valueProvider,
            final SerializableFunction<E, String> styleGenerator) {
        final Column<E> column = grid.addColumn(valueProvider);
        commonColumnConfiguration(column, styleGenerator);
        return column;
    }

    /**
     * Remove column based on it's id
     * 
     * @param grid
     *            to add the column to
     * @param columnId
     *            of the column
     * @param <E>
     *            entity type of the grid
     */
    public static <E> void removeColumn(final Grid<E> grid, final String columnId) {
        grid.removeColumnByKey(columnId);
    }

    /**
     * Check if a column is present based on it's id
     *
     * @param grid
     *            to add the column to
     * @param columnId
     *            of the column
     * @param <E>
     *            entity type of the grid
     * @return if the column is present
     */
    public static <E> boolean isColumnPresent(final Grid<E> grid, final String columnId) {
        return grid.getColumnByKey(columnId) != null;
    }

    /**
     * Add column to grid with the standard settings
     *
     * @param <E>
     *            entity type of the grid
     * @param grid
     *            to add the column to
     * @param componentProvider
     *            providing the content
     * @return the created column
     */
    public static <E, T extends Component> Column<E> addComponentColumn(final Grid<E> grid,
            final ValueProvider<E, T> componentProvider) {
        return addComponentColumn(grid, componentProvider, null);
    }

    /**
     * Add column to grid with the standard settings
     *
     * @param <E>
     *            entity type of the grid
     * @param grid
     *            to add the column to
     * @param componentProvider
     *            providing the content
     * @param styleGenerator
     *            providing additional styles
     * @return the created column
     */
    public static <E, T extends Component> Column<E> addComponentColumn(final Grid<E> grid,
            final ValueProvider<E, T> componentProvider, final SerializableFunction<E, String> styleGenerator) {
        final Column<E> column = grid.addComponentColumn(componentProvider);
        commonColumnConfiguration(column, styleGenerator);
        return column;
    }

    private static <E, T> Column<E> commonColumnConfiguration(final Column<E> column,
            final SerializableFunction<E, String> styleGenerator) {
        column.setFlexGrow(1);
        column.setSortable(false);

        if (styleGenerator != null) {
            column.setPartNameGenerator(styleGenerator);
        }
        return column;
    }

    /**
     * Add delete button column to grid
     *
     * @param <E>
     *            entity type of the grid
     * @param grid
     *            to add the column to
     * @param i18n
     *            message source for internationalization
     * @param columnId
     *            column ID
     * @param deleteSupport
     *            that executes the deletion
     * @param buttonIdPrefix
     *            prefix to create the button IDs
     * @param buttonEnabled
     *            is the button enabled
     * @return the created column
     */
    public static <E extends ProxyIdentifiableEntity> Column<E> addDeleteColumn(final Grid<E> grid,
            final VaadinMessageSource i18n, final String columnId, final DeleteSupport<E> deleteSupport,
            final String buttonIdPrefix, final Predicate<E> buttonEnabled) {
        final ValueProvider<E, Button> getDelButton = entity -> buildActionButton(i18n,
                clickEvent -> deleteSupport.openConfirmationWindowDeleteAction(entity), VaadinIcon.TRASH,
                UIMessageIdProvider.TOOLTIP_DELETE, SPUIStyleDefinitions.STATUS_ICON_NEUTRAL,
                buttonIdPrefix + "." + entity.getId(), buttonEnabled.test(entity));
        return addIconColumn(grid, getDelButton, columnId, i18n.getMessage("header.action.delete"))
                .setWidth(toPixels(60D));
    }

    /**
     * Add an action button column to a grid
     *
     * @param <T>
     *            type of the entity displayed by the grid
     * @param grid
     *            to add the column to
     * @param iconProvider
     *            to get the icon from
     * @param columnId
     *            column ID
     * @param caption
     *            caption of the column
     * @return the created column
     */
    public static <T, V extends Component> Column<T> addIconColumn(final Grid<T> grid,
            final ValueProvider<T, V> iconProvider, final String columnId, final String caption) {
        return addIconColumn(grid, iconProvider, columnId, caption, null);
    }

    /**
     * Add an action button column to a grid
     *
     * @param <T>
     *            type of the entity displayed by the grid
     * @param grid
     *            to add the column to
     * @param iconProvider
     *            to get the icon from
     * @param columnId
     *            column ID
     * @param caption
     *            caption of the column
     * @param styleGenerator
     *            caption of the column
     * @return the created column
     */
    public static <T, V extends Component> Column<T> addIconColumn(final Grid<T> grid,
            final ValueProvider<T, V> iconProvider, final String columnId, final String caption,
            final SerializableFunction<T, String> styleGenerator) {
        final SerializableFunction<T, String> additionalStyleGenerator = entity -> SPUIStyleDefinitions.ICON_CELL;

        final SerializableFunction<T, String> finalStyleGenerator = merge(
                Arrays.asList(styleGenerator, additionalStyleGenerator));

        final Column<T> column = grid.addComponentColumn(iconProvider).setKey(columnId)
                .setPartNameGenerator(finalStyleGenerator).setWidth(toPixels(60D)).setFlexGrow(0).setResizable(false)
                .setSortable(false);
        if (!StringUtils.isEmpty(caption)) {
            column.setHeader(caption);
        }
        return column;
    }

    /**
     * Makes the column sortable.
     *
     * @param <T>
     *            type of the entity displayed by the grid
     * @param <V>
     *            type of column value
     * @param column
     *            the column to set sortable
     * @param sortPropertyName
     *            the jpa property name to sort by
     * @return the provided column
     */
    public static <T, V> Column<T> setColumnSortable(final Column<T> column,
            final String sortPropertyName) {
        column.setSortable(true).setSortProperty(sortPropertyName);
        return column;
    }

    private static <T> SerializableFunction<T, String> merge(
            final Collection<SerializableFunction<T, String>> generators) {
        return item -> generators.stream().filter(Objects::nonNull).map(gen -> gen.apply(item)).filter(Objects::nonNull)
                .collect(Collectors.joining(" "));
    }

    /**
     * Join columns to form an action column
     *
     * @param i18n
     *            message source for internationalization
     * @param headerRow
     *            header row
     * @param columns
     *            columns to join
     */
    public static void joinToActionColumn(final VaadinMessageSource i18n, final HeaderRow headerRow,
            final List<Column<?>> columns) {
        joinToIconColumn(headerRow, i18n.getMessage("header.action"), columns);
    }

    /**
     * Join columns to form an icon column
     *
     * @param headerRow
     *            header row
     * @param headerCaption
     *            header caption
     * @param columns
     *            columns to join
     */
    public static void joinToIconColumn(final HeaderRow headerRow, final String headerCaption,
            final List<Column<?>> columns) {
        columns.forEach(column -> {
            column.setWidth(toPixels(30D));
            column.setFlexGrow(0);
            column.setResizable(false);
        });
        // Flow cannot join cells in the first header row (Vaadin 8's default row, the one with the column
        // captions; HeaderRow.join throws): the caption goes into the first column's cell, the others stay empty
        headerRow.getCell(columns.get(0)).setText(headerCaption);
        columns.stream().skip(1).forEach(column -> headerRow.getCell(column).setText(""));
    }

    /**
     * Create an action button (e.g. a delete button)
     *
     * @param i18n
     *            message source for internationalization
     * @param clickListener
     *            clickListener
     * @param icon
     *            icon of the button
     * @param descriptionMsgProperty
     *            displayed as tool tip
     * @param style
     *            additional style
     * @param buttonId
     *            ID of the button
     * @param enabled
     *            is the button enabled
     * @return the button
     */
    public static Button buildActionButton(final VaadinMessageSource i18n, final ComponentEventListener<ClickEvent<Button>> clickListener,
            final VaadinIcon icon, final String descriptionMsgProperty, final String style, final String buttonId,
            final boolean enabled) {
        final Button actionButton = new Button();

        actionButton.addClickListener(clickListener);
        actionButton.setIcon(icon.create());
        actionButton.setAriaLabel(i18n.getMessage(descriptionMsgProperty));
        actionButton.setTooltipText(i18n.getMessage(descriptionMsgProperty));
        actionButton.setEnabled(enabled);
        actionButton.setId(buttonId);
        actionButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ICON);
        actionButton.addClassNames("borderless-colored", "button-no-border", "action-type-padding", "icon-only");
        if (!StringUtils.isEmpty(style)) {
            actionButton.addClassNames(style.trim().split("\\s+"));
        }

        return actionButton;
    }

    // Vaadin 8 column widths were numbers of pixels
    private static String toPixels(final double width) {
        return ((int) width) + "px";
    }
}
