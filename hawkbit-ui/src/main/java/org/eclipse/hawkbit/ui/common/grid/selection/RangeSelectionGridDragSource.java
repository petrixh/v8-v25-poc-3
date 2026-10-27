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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.eclipse.hawkbit.ui.common.data.proxies.ProxyIdentifiableEntity;
import org.eclipse.hawkbit.ui.common.grid.AbstractGrid;

import com.vaadin.flow.component.ComponentEventListener;
import com.vaadin.flow.component.ComponentUtil;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.grid.dnd.GridDragStartEvent;
import com.vaadin.flow.function.SerializableConsumer;
import com.vaadin.flow.shared.Registration;

/**
 * Makes the rows of a grid draggable and offers the number of currently
 * selected items to the client side, which shows it on the drag image.
 *
 * Vaadin 25 (add-on A4): Vaadin 8 extended GridDragSource with a GWT
 * connector. Flow's grid drags all selected rows when a selected row is
 * dragged and shows their count ({@code setSelectionDragDetails}). The drag
 * data stays on the server, in the UI, so that a drop target (another grid)
 * can read it, as Vaadin 8's {@code getDragSourceExtension().getDragData()}
 * did.
 *
 * @param <T>
 *            item type
 */
public class RangeSelectionGridDragSource<T extends ProxyIdentifiableEntity> implements Serializable {
    private static final long serialVersionUID = 1L;

    private static final String ACTIVE_DRAG_KEY = RangeSelectionGridDragSource.class.getName() + ".activeDrag";

    private final AbstractGrid<T, ?> grid;
    private final Map<String, String> dataTransferData = new HashMap<>();
    private final List<Registration> registrations = new ArrayList<>();

    /**
     * Constructor
     * 
     * @param target
     *            Grid to be extended.
     */
    public RangeSelectionGridDragSource(final AbstractGrid<T, ?> target) {
        this.grid = target;
        grid.setRowsDraggable(true);

        if (target.hasSelectionSupport()) {
            registrations.add(target.addSelectionListener(
                    event -> updateSelectionDragDetails(event.getAllSelectedItems().size())));
        }
    }

    private void updateSelectionDragDetails(final int selectionCount) {
        grid.setSelectionDragDetails(selectionCount, new HashMap<>(dataTransferData));
    }

    /**
     * Sets data transfer data of the given type for every drag from this grid.
     *
     * @param type
     *            data type
     * @param data
     *            data
     */
    public void setDataTransferData(final String type, final String data) {
        dataTransferData.put(type, data);
        grid.setDragDataGenerator(type, item -> data);
        updateSelectionDragDetails(grid.getSelectedItems().size());
    }

    /**
     * @param listener
     *            called when rows are dragged
     * @return the listener registration
     */
    public Registration addGridDragStartListener(final ComponentEventListener<GridDragStartEvent<T>> listener) {
        final Registration registration = grid.addDragStartListener(listener);
        registrations.add(registration);
        return registration;
    }

    /**
     * @param listener
     *            called when the drag ends; {@code true} when it was canceled
     *            (dropped where no drop target accepted it)
     * @return the listener registration
     */
    public Registration addGridDragEndListener(final SerializableConsumer<Boolean> listener) {
        final Registration registration = grid.getElement()
                .addEventListener("dragend", event -> {
                    final boolean canceled = "none"
                            .equals(event.getEventData().path("event.dataTransfer.dropEffect").asString(""));
                    listener.accept(canceled);
                    grid.getUI().ifPresent(ui -> ComponentUtil.setData(ui, ACTIVE_DRAG_KEY, null));
                }).addEventData("event.dataTransfer.dropEffect");
        registrations.add(registration);
        return registration;
    }

    /**
     * Sets the server-side drag data of the current drag.
     *
     * @param dragData
     *            the dragged items
     */
    public void setDragData(final Object dragData) {
        grid.getUI().ifPresent(ui -> ComponentUtil.setData(ui, ACTIVE_DRAG_KEY, new ActiveDrag(this, dragData)));
    }

    /**
     * @return the server-side drag data of the current drag from this grid
     */
    public Object getDragData() {
        return grid.getUI().map(ui -> (ActiveDrag) ComponentUtil.getData(ui, ACTIVE_DRAG_KEY))
                .filter(drag -> drag.getSource() == this).map(ActiveDrag::getDragData).orElse(null);
    }

    /**
     * The grid drag source of the drag in progress in the given UI, if any.
     *
     * @param ui
     *            the UI
     * @return the drag source
     */
    public static Optional<RangeSelectionGridDragSource<?>> getActiveDragSource(final UI ui) {
        return Optional.ofNullable((ActiveDrag) ComponentUtil.getData(ui, ACTIVE_DRAG_KEY))
                .map(ActiveDrag::getSource);
    }

    /**
     * Makes the rows not draggable anymore.
     */
    public void remove() {
        registrations.forEach(Registration::remove);
        registrations.clear();
        grid.setRowsDraggable(false);
    }

    private static final class ActiveDrag implements Serializable {
        private static final long serialVersionUID = 1L;

        private final RangeSelectionGridDragSource<?> source;
        private final transient Object dragData;

        private ActiveDrag(final RangeSelectionGridDragSource<?> source, final Object dragData) {
            this.source = source;
            this.dragData = dragData;
        }

        private RangeSelectionGridDragSource<?> getSource() {
            return source;
        }

        private Object getDragData() {
            return dragData;
        }
    }
}
