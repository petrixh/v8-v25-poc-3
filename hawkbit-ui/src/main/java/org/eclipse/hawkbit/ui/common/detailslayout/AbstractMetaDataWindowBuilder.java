/**
 * Copyright (c) 2020 Bosch.IO GmbH and others
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.hawkbit.ui.common.detailslayout;

import org.eclipse.hawkbit.ui.common.AbstractEntityWindowBuilder;
import org.eclipse.hawkbit.ui.common.CommonDialogWindow;
import org.eclipse.hawkbit.ui.common.CommonUiDependencies;
import org.eclipse.hawkbit.ui.common.data.proxies.ProxyMetaData;
import org.eclipse.hawkbit.ui.utils.UIComponentIdProvider;

import com.vaadin.flow.component.dialog.Dialog;

/**
 * Abstract builder for metadata window
 *
 * @param <F>
 *            Generic type
 */
public abstract class AbstractMetaDataWindowBuilder<F> extends AbstractEntityWindowBuilder<ProxyMetaData> {

    /**
     * Constructor for AbstractMetaDataWindowBuilder
     *
     * @param uiDependencies
     *            {@link CommonUiDependencies}
     */
    protected AbstractMetaDataWindowBuilder(final CommonUiDependencies uiDependencies) {
        super(uiDependencies);
    }

    @Override
    protected String getWindowId() {
        return UIComponentIdProvider.METADATA_POPUP_ID;
    }

    protected Dialog getWindowForShowMetaData(final AbstractMetaDataWindowLayout<F> metaDataWindowLayout,
            final F selectedEntityFilter, final String selectedEntityName, final ProxyMetaData proxyMetaData) {
        final CommonDialogWindow window = createWindow(metaDataWindowLayout, null);

        // Vaadin 8: caption with an assistive prefix for screen readers (no
        // public Flow API for the dialog's aria label)
        window.setHeaderTitle(selectedEntityName);

        metaDataWindowLayout.addValidationListener(window::setSaveButtonEnabled);
        metaDataWindowLayout.setSaveCallback(window::setCloseListener);
        metaDataWindowLayout.setMasterEntityFilter(selectedEntityFilter, proxyMetaData);

        window.setHeight("600px");
        window.setWidth("800px");

        return window;
    }

    @Override
    public Dialog getWindowForAdd() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Dialog getWindowForUpdate(final ProxyMetaData entity) {
        throw new UnsupportedOperationException();
    }
}
