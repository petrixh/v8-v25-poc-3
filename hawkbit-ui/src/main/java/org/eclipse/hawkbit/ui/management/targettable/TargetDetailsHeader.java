/**
 * Copyright (c) 2020 Bosch.IO GmbH and others
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.hawkbit.ui.management.targettable;

import org.eclipse.hawkbit.ui.common.CommonUiDependencies;
import org.eclipse.hawkbit.ui.common.data.proxies.ProxyTarget;
import org.eclipse.hawkbit.ui.common.grid.header.AbstractDetailsHeader;
import org.eclipse.hawkbit.ui.utils.UIComponentIdProvider;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.dialog.Dialog;

/**
 * Header for target details
 */
public class TargetDetailsHeader extends AbstractDetailsHeader<ProxyTarget> {
    private static final long serialVersionUID = 1L;

    private final transient TargetWindowBuilder targetWindowBuilder;
    private final transient TargetMetaDataWindowBuilder targetMetaDataWindowBuilder;

    /**
     * Constructor for TargetDetailsHeader
     *
     * @param uiDependencies
     *            {@link CommonUiDependencies}
     * @param targetWindowBuilder
     *            TargetWindowBuilder
     * @param targetMetaDataWindowBuilder
     *            TargetMetaDataWindowBuilder
     */
    public TargetDetailsHeader(final CommonUiDependencies uiDependencies, final TargetWindowBuilder targetWindowBuilder,
            final TargetMetaDataWindowBuilder targetMetaDataWindowBuilder) {
        super(uiDependencies.getI18n(), uiDependencies.getPermChecker(), uiDependencies.getEventBus(), uiDependencies.getUiNotification());

        this.targetWindowBuilder = targetWindowBuilder;
        this.targetMetaDataWindowBuilder = targetMetaDataWindowBuilder;

        buildHeader();
    }

    @Override
    protected String getMasterEntityType() {
        return i18n.getMessage("target.details.header");
    }

    @Override
    protected String getMasterEntityDetailsCaptionId() {
        return UIComponentIdProvider.TARGET_DETAILS_HEADER_LABEL_ID;
    }

    @Override
    protected String getMasterEntityName(final ProxyTarget masterEntity) {
        return masterEntity.getName();
    }

    @Override
    protected boolean hasEditPermission() {
        return permChecker.hasUpdateTargetPermission();
    }

    @Override
    protected String getEditIconId() {
        return UIComponentIdProvider.TARGET_EDIT_ICON;
    }

    @Override
    protected void onEdit() {
        if (selectedEntity == null) {
            return;
        }

        final Dialog updateWindow = targetWindowBuilder.getWindowForUpdate(selectedEntity);

        updateWindow.setCaption(i18n.getMessage("caption.update", i18n.getMessage("caption.target")));
        updateWindow.open();
        updateWindow.setVisible(Boolean.TRUE);
    }

    @Override
    protected String getMetaDataIconId() {
        return UIComponentIdProvider.TARGET_METADATA_BUTTON;
    }

    @Override
    protected void showMetaData() {
        if (selectedEntity == null) {
            return;
        }

        final Dialog metaDataWindow = targetMetaDataWindowBuilder
                .getWindowForShowTargetMetaData(selectedEntity.getControllerId(), selectedEntity.getName());

        metaDataWindow.open();
        metaDataWindow.setVisible(Boolean.TRUE);
    }
}
