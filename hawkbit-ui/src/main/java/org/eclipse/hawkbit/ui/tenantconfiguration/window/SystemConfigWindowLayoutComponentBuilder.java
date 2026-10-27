/**
 * Copyright (c) 2020 Bosch.IO GmbH and others
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.hawkbit.ui.tenantconfiguration.window;

import org.eclipse.hawkbit.ui.common.data.proxies.ProxySystemConfigDsType;
import org.eclipse.hawkbit.ui.common.data.proxies.ProxyTypeInfo;
import org.eclipse.hawkbit.ui.components.SPUIComponentProvider;
import org.eclipse.hawkbit.ui.utils.UIComponentIdProvider;

import com.vaadin.flow.data.binder.Binder;
import com.vaadin.server.Sizeable.Unit;
import com.vaadin.flow.component.combobox.ComboBox;

/**
 * Builder for system config window layout component
 */
public class SystemConfigWindowLayoutComponentBuilder {
    private final SystemConfigWindowDependencies dependencies;

    /**
     * Constructor for SystemConfigWindowLayoutComponentBuilder
     *
     * @param dependencies
     *            SystemConfigWindowDependencies
     */
    public SystemConfigWindowLayoutComponentBuilder(final SystemConfigWindowDependencies dependencies) {
        this.dependencies = dependencies;
    }

    /**
     * Create the distribution set type combo
     *
     * @param binder
     *            System config window binder
     *
     * @return Distribution set type combo box
     */
    public ComboBox<ProxyTypeInfo> createDistributionSetTypeCombo(final Binder<ProxySystemConfigDsType> binder) {
        final ComboBox<ProxyTypeInfo> dsTypeCombo = SPUIComponentProvider.getComboBox(
                UIComponentIdProvider.SYSTEM_CONFIGURATION_DEFAULTDIS_COMBOBOX, null,
                dependencies.getI18n().getMessage("caption.type"), null, false, ProxyTypeInfo::getKeyAndName,
                dependencies.getDistributionSetTypeDataProvider());
        dsTypeCombo.removeClassNames("small");
        dsTypeCombo.addClassNames("tiny");
        dsTypeCombo.setWidth("330px");

        binder.forField(dsTypeCombo).bind(ProxySystemConfigDsType::getDsTypeInfo,
                ProxySystemConfigDsType::setDsTypeInfo);

        return dsTypeCombo;
    }
}
