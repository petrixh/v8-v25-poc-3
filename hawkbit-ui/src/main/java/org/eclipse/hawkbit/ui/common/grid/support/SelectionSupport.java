/**
 * Copyright (c) 2020 Bosch.IO GmbH and others
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.hawkbit.ui.common.grid.support;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.LongFunction;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import jakarta.annotation.PreDestroy;

import org.eclipse.hawkbit.ui.common.data.proxies.ProxyIdentifiableEntity;
import org.eclipse.hawkbit.ui.common.event.EventLayout;
import org.eclipse.hawkbit.ui.common.event.EventTopics;
import org.eclipse.hawkbit.ui.common.event.EventView;
import org.eclipse.hawkbit.ui.common.event.SelectionChangedEventPayload;
import org.eclipse.hawkbit.ui.common.event.SelectionChangedEventPayload.SelectionChangedEventType;
import org.springframework.util.CollectionUtils;
import org.vaadin.spring.events.EventBus.UIEventBus;

import com.vaadin.flow.shared.Registration;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.Grid.SelectionMode;
import com.vaadin.flow.component.grid.GridMultiSelectionModel;
import com.vaadin.flow.component.grid.GridMultiSelectionModel.SelectAllCheckboxVisibility;
import com.vaadin.flow.component.grid.GridNoneSelectionModel;
import com.vaadin.flow.component.grid.GridSingleSelectionModel;
import com.vaadin.flow.data.provider.DataKeyMapper;
import com.vaadin.flow.data.provider.DataProvider;
import com.vaadin.flow.data.provider.Query;

/**
 * Support for selection on the grid.
 * 
 * @param <T>
 *            The item-type used by the grid
 */
public class SelectionSupport<T extends ProxyIdentifiableEntity> {
    private final Grid<T> grid;
    private final UIEventBus eventBus;
    private final EventLayout layout;
    private final EventView view;

    private final LongFunction<Optional<T>> mapIdToProxyEntityFunction;
    private final Supplier<Long> selectedEntityIdUiStateProvider;
    private final Consumer<Long> setSelectedEntityIdUiStateCallback;

    private Registration singleSelectListenerRegistration;
    private Registration multiSelectListenerRegistration;

    /**
     * Constructor for grids without selection support
     *
     * @param grid
     *            Vaadin grid
     */
    public SelectionSupport(final Grid<T> grid) {
        this(grid, null, null, null, null, null, null);
    }

    /**
     * Constructor for grids with selection support
     *
     * @param grid
     *            Vaadin grid
     * @param eventBus
     *            UIEventBus
     * @param layout
     *            UIEventBus
     * @param view
     *            EventView
     * @param mapIdToProxyEntityFunction
     *            Function to map id to proxy entity
     * @param selectedEntityIdUiStateProvider
     *            Selected entity id provider
     * @param setSelectedEntityIdUiStateCallback
     *            Callback event to set selected entity id
     */
    // UI state values are nullable
    @SuppressWarnings("squid:S4276")
    public SelectionSupport(final Grid<T> grid, final UIEventBus eventBus, final EventLayout layout,
            final EventView view, final LongFunction<Optional<T>> mapIdToProxyEntityFunction,
            final Supplier<Long> selectedEntityIdUiStateProvider,
            final Consumer<Long> setSelectedEntityIdUiStateCallback) {
        this.grid = grid;
        this.eventBus = eventBus;
        this.layout = layout;
        this.view = view;

        this.mapIdToProxyEntityFunction = mapIdToProxyEntityFunction;
        this.selectedEntityIdUiStateProvider = selectedEntityIdUiStateProvider;
        this.setSelectedEntityIdUiStateCallback = setSelectedEntityIdUiStateCallback;
    }

    /**
     * Disable the selection mode in grid
     */
    public final void disableSelection() {
        grid.setSelectionMode(SelectionMode.NONE);

        removeListeners();
    }

    private void removeListeners() {
        if (singleSelectListenerRegistration != null) {
            singleSelectListenerRegistration.remove();
            singleSelectListenerRegistration = null;
        }

        if (multiSelectListenerRegistration != null) {
            multiSelectListenerRegistration.remove();
            multiSelectListenerRegistration = null;
        }
    }

    /**
     * Enable the single selection in grid
     */
    public final void enableSingleSelection() {
        grid.setSelectionMode(SelectionMode.SINGLE);

        singleSelectListenerRegistration = ((GridSingleSelectionModel<T>) grid.getSelectionModel())
                .addSingleSelectionListener(event -> {
            final SelectionChangedEventType selectionType = event.getSelectedItem().isPresent()
                    ? SelectionChangedEventType.ENTITY_SELECTED
                    : SelectionChangedEventType.ENTITY_DESELECTED;
            final T itemToSend = event.getSelectedItem().orElse(event.getOldValue());

            sendSelectionChangedEvent(selectionType, itemToSend);
        });
    }

    /**
     * Send selection change event
     *
     * @param selectionType
     *            SelectionChangedEventType
     * @param itemToSend
     *            selected item to send
     *
     */
    public void sendSelectionChangedEvent(final SelectionChangedEventType selectionType, final T itemToSend) {
        if (eventBus == null) {
            return;
        }

        if (SelectionChangedEventType.ENTITY_SELECTED == selectionType && itemToSend == null) {
            return;
        }

        eventBus.publish(EventTopics.SELECTION_CHANGED, grid,
                new SelectionChangedEventPayload<>(selectionType, itemToSend, layout, view));

        updateUiState(selectionType, itemToSend);
    }

    private void updateUiState(final SelectionChangedEventType selectionType, final T itemToSend) {
        if (setSelectedEntityIdUiStateCallback == null) {
            return;
        }

        final Long selectedItemId = SelectionChangedEventType.ENTITY_SELECTED == selectionType ? itemToSend.getId()
                : null;
        setSelectedEntityIdUiStateCallback.accept(selectedItemId);
    }

    /**
     * Enable multiple selection in grid
     */
    public final void enableMultiSelection() {
        grid.setSelectionMode(SelectionMode.MULTI);

        final GridMultiSelectionModel<T> multiSelectionModel = (GridMultiSelectionModel<T>) grid.getSelectionModel();
        multiSelectionModel.setSelectAllCheckboxVisibility(SelectAllCheckboxVisibility.VISIBLE);
        multiSelectListenerRegistration = multiSelectionModel.addMultiSelectionListener(event -> {
            if (event.getAllSelectedItems().size() == 1) {
                sendSelectionChangedEvent(SelectionChangedEventType.ENTITY_SELECTED,
                        event.getAllSelectedItems().iterator().next());
            } else if (event.getOldSelection().size() == 1) {
                sendSelectionChangedEvent(SelectionChangedEventType.ENTITY_DESELECTED,
                        event.getOldSelection().iterator().next());
            }
        });
    }

    /**
     * @return true if grid selection model is not enable else false
     */
    public boolean isNoSelectionModel() {
        return grid.getSelectionModel() instanceof GridNoneSelectionModel;
    }

    public boolean isSingleSelectionModel() {
        return grid.getSelectionModel() instanceof GridSingleSelectionModel;
    }

    /**
     * @return true if grid selection model is multi selection else false
     */
    public boolean isMultiSelectionModel() {
        return grid.getSelectionModel() instanceof GridMultiSelectionModel;
    }

    /**
     * Vaadin 8: the selection model replaced a selected item with the instance
     * last fetched for its row (AbstractSelectionModel#generateData), so a
     * drag, an invalidation or a deletion saw the item's current state, e.g.
     * an invalidated distribution set. Flow keeps the instance that was selected: take the instance the
     * grid last fetched for the row where the grid has it.
     *
     * @param grid
     *            the grid
     * @return the selected items, as last fetched
     */
    public static <T> List<T> getSelectedItemsAsLastFetched(final Grid<T> grid) {
        final DataKeyMapper<T> keyMapper = grid.getDataCommunicator().getKeyMapper();

        return grid.getSelectedItems().stream()
                .map(item -> keyMapper.has(item) ? keyMapper.get(keyMapper.key(item)) : item)
                .collect(Collectors.toCollection(ArrayList::new));
    }

    /**
     * @return Selected items from the grid
     */
    public Set<T> getSelectedItems() {
        if (isNoSelectionModel()) {
            return Collections.emptySet();
        }

        return grid.getSelectedItems();
    }

    /**
     * @return Selected entity from the grid
     */
    public Optional<T> getSelectedEntity() {
        final Set<T> selectedItems = getSelectedItems();

        if (selectedItems.size() == 1) {
            return Optional.of(selectedItems.iterator().next());
        }

        return Optional.empty();
    }

    /**
     * @return Id of selected entity from the grid
     */
    public Optional<Long> getSelectedEntityId() {
        if (isNoSelectionModel() && selectedEntityIdUiStateProvider != null) {
            return Optional.ofNullable(selectedEntityIdUiStateProvider.get());
        }

        return getSelectedEntity().map(ProxyIdentifiableEntity::getId);
    }

    /**
     * Selects the first row if available and enabled.
     *
     * @return True if first row is selected else false
     */
    public boolean selectFirstRow() {
        if (isNoSelectionModel()) {
            return false;
        }

        final List<T> firstItem = fetchFirstItem();

        if (!CollectionUtils.isEmpty(firstItem)) {
            grid.select(firstItem.get(0));

            return true;
        }

        grid.deselectAll();

        return false;
    }

    // Vaadin 8: DataCommunicator#fetchItemsWithRange(0, 1); Flow: the grid's
    // query (filter, sort) for the first row
    @SuppressWarnings({ "unchecked", "rawtypes" })
    private List<T> fetchFirstItem() {
        final Query<T, Object> query = grid.getDataCommunicator().buildQuery(0, 1);
        return ((DataProvider<T, Object>) grid.getDataProvider()).fetch(query).toList();
    }

    /**
     * Select the item in grid
     *
     * @param itemToSelect
     *            Item for selection
     */
    public void select(final T itemToSelect) {
        if (isNoSelectionModel()) {
            return;
        }

        grid.select(itemToSelect);
    }

    /**
     * Select entity based on Id
     *
     * @param entityId
     *            Entity id
     */
    public void selectEntityById(final Long entityId) {
        if (isNoSelectionModel()) {
            return;
        }

        if (mapIdToProxyEntityFunction == null || entityId == null) {
            return;
        }

        mapIdToProxyEntityFunction.apply(entityId).ifPresent(this::select);
    }

    /**
     * Select all items or entities in the grid
     */
    public void selectAll() {
        if (!isMultiSelectionModel()) {
            return;
        }

        ((GridMultiSelectionModel<T>) grid.getSelectionModel()).selectAll();
    }

    /**
     * Deselect the item from the grid
     *
     * @param itemToDeselect
     *            Item for selection
     */
    public void deselect(final T itemToDeselect) {
        if (isNoSelectionModel()) {
            return;
        }

        grid.deselect(itemToDeselect);
    }

    /**
     * Clears the selection.
     */
    public void deselectAll() {
        if (isNoSelectionModel()) {
            return;
        }

        if (!getSelectedItems().isEmpty()) {
            grid.deselectAll();
        }
    }

    /**
     * Restores the last selection
     */
    public void restoreSelection() {
        if (selectedEntityIdUiStateProvider == null) {
            return;
        }

        final Long lastSelectedEntityId = selectedEntityIdUiStateProvider.get();

        if (lastSelectedEntityId != null) {
            selectEntityById(lastSelectedEntityId);
        } else {
            selectFirstRow();
        }
    }

    /**
     * Re-fetches and re-selects currently selected entity
     */
    public void reselectCurrentEntity() {
        getSelectedEntityId().ifPresent(selectedEntityId -> {
            deselectAll();
            selectEntityById(selectedEntityId);
        });
    }

    @PreDestroy
    void destroy() {
        removeListeners();
    }
}
