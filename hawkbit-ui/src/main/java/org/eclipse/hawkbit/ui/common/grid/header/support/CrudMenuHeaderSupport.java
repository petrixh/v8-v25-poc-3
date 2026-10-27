/**
 * Copyright (c) 2020 Bosch.IO GmbH and others
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.hawkbit.ui.common.grid.header.support;

import org.eclipse.hawkbit.ui.utils.UIMessageIdProvider;
import org.eclipse.hawkbit.ui.utils.VaadinMessageSource;

import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.menubar.MenuBar;
import com.vaadin.flow.component.contextmenu.MenuItem;
import com.vaadin.flow.shared.Registration;

/**
 * Header support for CRUD menu
 */
public class CrudMenuHeaderSupport implements HeaderSupport {
    private final VaadinMessageSource i18n;

    private final String crudMenuBarId;
    private final boolean hasCreatePermission;
    private final boolean hasUpdatePermission;
    private final boolean hasDeletePermission;
    private final Runnable addCallback;
    private final Runnable editCallback;
    private final Runnable deleteCallback;
    private final Runnable closeCallback;

    private final MenuBar crudMenuBar;
    private final MenuItem crudMenuItem;

    private Mode currentMode;
    // Vaadin 8: the command of the menu item
    private Registration crudMenuItemCommand;

    /**
     * Constructor for CrudMenuHeaderSupport
     *
     * @param i18n
     *            VaadinMessageSource
     * @param crudMenuBarId
     *            Crud menu bar id
     * @param hasCreatePermission
     *            boolean value based on create permission
     * @param hasUpdatePermission
     *            boolean value based on update permission
     * @param hasDeletePermission
     *            boolean value based on delete permission
     * @param addCallback
     *            Runnable
     * @param editCallback
     *            Runnable
     * @param deleteCallback
     *            Runnable
     * @param closeCallback
     *            Runnable
     */
    public CrudMenuHeaderSupport(final VaadinMessageSource i18n, final String crudMenuBarId,
            final boolean hasCreatePermission, final boolean hasUpdatePermission, final boolean hasDeletePermission,
            final Runnable addCallback, final Runnable editCallback, final Runnable deleteCallback,
            final Runnable closeCallback) {
        this.i18n = i18n;

        this.crudMenuBarId = crudMenuBarId;
        this.hasCreatePermission = hasCreatePermission;
        this.hasUpdatePermission = hasUpdatePermission;
        this.hasDeletePermission = hasDeletePermission;
        this.addCallback = addCallback;
        this.editCallback = editCallback;
        this.deleteCallback = deleteCallback;
        this.closeCallback = closeCallback;

        this.crudMenuBar = createCrudMenuBar();
        this.crudMenuItem = crudMenuBar.addItem("");

        addCrudMenuItemCommands();
        activateSelectMode();
    }

    private MenuBar createCrudMenuBar() {
        final MenuBar menuBar = new MenuBar();

        menuBar.setId(crudMenuBarId);
        menuBar.setClassName("borderless");
        menuBar.addClassNames("crud-menubar");

        return menuBar;
    }

    private void addCrudMenuItemCommands() {
        if (hasCreatePermission) {
            addSubItem(i18n.getMessage(UIMessageIdProvider.CAPTION_CONFIG_CREATE), VaadinIcon.PLUS,
                    addCallback);
        }
        if (hasUpdatePermission) {
            addSubItem(i18n.getMessage(UIMessageIdProvider.CAPTION_CONFIG_EDIT), VaadinIcon.EDIT, () -> {
                activateModifyMode(Mode.EDIT);
                editCallback.run();
            });
        }
        if (hasDeletePermission) {
            addSubItem(i18n.getMessage(UIMessageIdProvider.CAPTION_CONFIG_DELETE), VaadinIcon.TRASH, () -> {
                activateModifyMode(Mode.DELETE);
                deleteCallback.run();
            });
        }

        // in case of missing CUD permissions we disable the menu button
        if (crudMenuItem.getSubMenu().getItems().isEmpty()) {
            crudMenuItem.setEnabled(false);
        }
    }

    private void addSubItem(final String caption, final VaadinIcon icon, final Runnable command) {
        final MenuItem subItem = crudMenuItem.getSubMenu().addItem(caption, menuItem -> command.run());
        subItem.addComponentAsFirst(icon.create());
    }

    // Vaadin 8: MenuItem#setIcon on an item without text
    private void setCrudMenuItemIcon(final VaadinIcon icon) {
        crudMenuItem.removeAll();
        crudMenuItem.add(icon.create());
    }

    private void setCrudMenuItemCommand(final Runnable command) {
        if (crudMenuItemCommand != null) {
            crudMenuItemCommand.remove();
            crudMenuItemCommand = null;
        }
        if (command != null) {
            crudMenuItemCommand = crudMenuItem.addClickListener(event -> command.run());
        }
    }

    private void activateSelectMode() {
        setCrudMenuItemIcon(VaadinIcon.COG);
        crudMenuItem.setTooltipText(i18n.getMessage(UIMessageIdProvider.TOOLTIP_CONFIGURE));
        setCrudMenuItemCommand(null);

        currentMode = Mode.SELECT;
        crudMenuBar.removeClassNames(Mode.EDIT.getStyle(), Mode.DELETE.getStyle());
    }

    private void activateModifyMode(final Mode mode) {
        setCrudMenuItemIcon(VaadinIcon.CLOSE_CIRCLE);
        crudMenuItem.setTooltipText(i18n.getMessage(UIMessageIdProvider.TOOLTIP_CONFIGURE_CLOSE));
        setCrudMenuItemCommand(this::resetModifyMode);

        currentMode = mode;
        crudMenuBar.addClassNames(mode.getStyle());
    }

    private void resetModifyMode() {
        if (isModifyModeActive()) {
            activateSelectMode();
            closeCallback.run();
        }
    }

    private boolean isModifyModeActive() {
        return Mode.EDIT == currentMode || Mode.DELETE == currentMode;
    }

    @Override
    public Component getHeaderComponent() {
        return crudMenuBar;
    }

    /**
     * Enable crud menu option
     */
    public void enableCrudMenu() {
        crudMenuBar.setEnabled(true);
    }

    /**
     * Disable crud menu option
     */
    public void disableCrudMenu() {
        resetModifyMode();
        crudMenuBar.setEnabled(false);
    }

    private enum Mode {
        SELECT(""), EDIT("mode-edit"), DELETE("mode-delete");

        private String style;

        Mode(final String style) {
            this.style = style;
        }

        private String getStyle() {
            return style;
        }
    }
}
