/**
 * Copyright (c) 2020 Bosch.IO GmbH and others
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.hawkbit.ui.distributions.dstable;

import org.eclipse.hawkbit.repository.DistributionSetTypeManagement;
import org.eclipse.hawkbit.repository.TenantConfigurationManagement;
import org.eclipse.hawkbit.repository.model.DistributionSetType;
import org.eclipse.hawkbit.security.SystemSecurityContext;
import org.eclipse.hawkbit.tenancy.configuration.TenantConfigurationProperties.TenantConfigurationKey;
import org.eclipse.hawkbit.ui.common.builder.WindowFormLayout;
import org.eclipse.hawkbit.ui.common.AbstractEntityWindowLayout;
import org.eclipse.hawkbit.ui.common.data.mappers.TypeToTypeInfoMapper;
import org.eclipse.hawkbit.ui.common.data.providers.DistributionSetTypeDataProvider;
import org.eclipse.hawkbit.ui.common.data.proxies.ProxyDistributionSet;
import org.eclipse.hawkbit.ui.common.data.proxies.ProxyTypeInfo;
import org.eclipse.hawkbit.ui.utils.VaadinMessageSource;

import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;

/**
 * Target add/update window layout.
 */
public class DsWindowLayout extends AbstractEntityWindowLayout<ProxyDistributionSet> {
    private final SystemSecurityContext systemSecurityContext;
    private final TenantConfigurationManagement tenantConfigurationManagement;

    private final DsWindowLayoutComponentBuilder dsComponentBuilder;

    private final ComboBox<ProxyTypeInfo> dsTypeSelect;
    private final TextField dsName;
    private final TextField dsVersion;
    private final TextArea dsDescription;
    private final Checkbox dsMigrationStepRequired;

    /**
     * Constructor for AbstractTagWindowLayout
     * 
     * @param i18n
     *          VaadinMessageSource
     * @param systemSecurityContext
     *          SystemSecurityContext
     * @param tenantConfigurationManagement
     *          TenantConfigurationManagement
     * @param dsTypeManagement
     *          DistributionSetTypeManagement
     */
    public DsWindowLayout(final VaadinMessageSource i18n, final SystemSecurityContext systemSecurityContext,
            final TenantConfigurationManagement tenantConfigurationManagement,
            final DistributionSetTypeManagement dsTypeManagement) {
        super();

        this.systemSecurityContext = systemSecurityContext;
        this.tenantConfigurationManagement = tenantConfigurationManagement;

        final DistributionSetTypeDataProvider<ProxyTypeInfo> dsTypeDataProvider = new DistributionSetTypeDataProvider<>(
                dsTypeManagement, new TypeToTypeInfoMapper<DistributionSetType>());
        this.dsComponentBuilder = new DsWindowLayoutComponentBuilder(i18n, dsTypeDataProvider);

        this.dsTypeSelect = dsComponentBuilder.createDistributionSetTypeCombo(binder);
        this.dsName = dsComponentBuilder.createNameField(binder);
        this.dsVersion = dsComponentBuilder.createVersionField(binder);
        this.dsDescription = dsComponentBuilder.createDescription(binder);
        this.dsMigrationStepRequired = isMultiAssignmentEnabled() ? null
                : dsComponentBuilder.createMigrationStepField(binder);
    }

    private boolean isMultiAssignmentEnabled() {
        return systemSecurityContext.runAsSystem(() -> tenantConfigurationManagement
                .getConfigurationValue(TenantConfigurationKey.MULTI_ASSIGNMENTS_ENABLED, Boolean.class).getValue());
    }

    @Override
    public Component getRootComponent() {
        final FormLayout dsWindowLayout = new WindowFormLayout();

        dsWindowLayout.setSizeUndefined();

        dsWindowLayout.add(dsTypeSelect);

        dsWindowLayout.add(dsName);
        dsName.focus();

        dsWindowLayout.add(dsVersion);

        dsWindowLayout.add(dsDescription);

        if (dsMigrationStepRequired != null) {
            dsWindowLayout.add(dsMigrationStepRequired);
        }

        return dsWindowLayout;
    }

    /**
     * Disable the distribution set type selection
     */
    public void disableDsTypeSelect() {
        dsTypeSelect.setEnabled(false);
    }
}
