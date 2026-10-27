/**
 * Copyright (c) 2020 Bosch.IO GmbH and others
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.hawkbit.ui.artifacts.smtable;

import java.util.function.BooleanSupplier;

import org.eclipse.hawkbit.ui.common.detailslayout.MetaDataAddUpdateWindowLayout;
import org.eclipse.hawkbit.ui.utils.VaadinMessageSource;

import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;

/**
 * Class for sm metadata add/update window layout.
 */
public class SmMetaDataAddUpdateWindowLayout extends MetaDataAddUpdateWindowLayout {
    protected final Checkbox isVisibleForTarget;

    /**
     * Constructor for AbstractTagWindowLayout
     * 
     * @param i18n
     *            VaadinMessageSource
     * @param hasMetadataChangePermission
     *            checks the permission allowing to change metadata entities
     */
    public SmMetaDataAddUpdateWindowLayout(final VaadinMessageSource i18n,
            final BooleanSupplier hasMetadataChangePermission) {
        super(i18n, hasMetadataChangePermission);

        this.isVisibleForTarget = metaDataComponentBuilder.createVisibleForTargetsField(binder);
    }

    @Override
    protected VerticalLayout getMetadataAddUpdateLayout() {
        final VerticalLayout addUpdateLayout = super.getMetadataAddUpdateLayout();
        addUpdateLayout.add(isVisibleForTarget);

        return addUpdateLayout;
    }

    @Override
    protected void disableMetadataInputComponents() {
        super.disableMetadataInputComponents();
        isVisibleForTarget.setEnabled(false);
    }
}
