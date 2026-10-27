/**
 * Copyright (c) 2015 Bosch Software Innovations GmbH and others
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.hawkbit.ui.tenantconfiguration;

import org.eclipse.hawkbit.repository.DistributionSetTypeManagement;
import org.eclipse.hawkbit.repository.SystemManagement;
import org.eclipse.hawkbit.repository.TenantConfigurationManagement;
import org.eclipse.hawkbit.repository.model.DistributionSetType;
import org.eclipse.hawkbit.ui.SpPermissionChecker;
import org.eclipse.hawkbit.ui.common.builder.LabelBuilder;
import org.eclipse.hawkbit.ui.common.data.mappers.TypeToTypeInfoMapper;
import org.eclipse.hawkbit.ui.common.data.providers.DistributionSetTypeDataProvider;
import org.eclipse.hawkbit.ui.common.data.proxies.ProxySystemConfigDsType;
import org.eclipse.hawkbit.ui.common.data.proxies.ProxyTypeInfo;
import org.eclipse.hawkbit.ui.tenantconfiguration.window.SystemConfigWindowDependencies;
import org.eclipse.hawkbit.ui.tenantconfiguration.window.SystemConfigWindowLayoutComponentBuilder;
import org.eclipse.hawkbit.ui.utils.VaadinMessageSource;

import com.vaadin.flow.component.HasValue;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;

/**
 * Default DistributionSet Panel.
 */
public class DefaultDistributionSetTypeView extends BaseConfigurationView<ProxySystemConfigDsType> {

    private static final long serialVersionUID = 1L;

    private final VaadinMessageSource i18n;

    private final SpPermissionChecker permissionChecker;
    private Long currentDefaultDistSetTypeId;
    private ComboBox<ProxyTypeInfo> dsSetTypeComboBox;
    private final transient SystemManagement systemManagement;
    private final transient SystemConfigWindowLayoutComponentBuilder builder;
    private Span changeIcon;

    DefaultDistributionSetTypeView(final VaadinMessageSource i18n,
            final TenantConfigurationManagement tenantConfigurationManagement, final SystemManagement systemManagement,
            final SpPermissionChecker permissionChecker, final DistributionSetTypeManagement dsTypeManagement) {
        super(tenantConfigurationManagement);
        this.i18n = i18n;
        this.permissionChecker = permissionChecker;
        this.systemManagement = systemManagement;
        final DistributionSetTypeDataProvider<ProxyTypeInfo> dataProvider = new DistributionSetTypeDataProvider<>(
                dsTypeManagement, new TypeToTypeInfoMapper<>());
        final SystemConfigWindowDependencies dependencies = new SystemConfigWindowDependencies(systemManagement, i18n,
                permissionChecker, dsTypeManagement, dataProvider);
        this.builder = new SystemConfigWindowLayoutComponentBuilder(dependencies);
    }

    @Override
    public void afterPropertiesSet() {
        super.afterPropertiesSet();
        init();
    }

    private void init() {
        if (!permissionChecker.hasReadRepositoryPermission()) {
            return;
        }
        final Div rootPanel = new Div();
        rootPanel.setSizeFull();
        rootPanel.addClassNames("config-panel");
        final VerticalLayout vlayout = new VerticalLayout();
        vlayout.setSpacing(false);
        vlayout.setPadding(true);
        vlayout.setSizeFull();

        final Span header = new Span(i18n.getMessage("configuration.defaultdistributionset.title"));
        header.addClassNames("config-panel-header");
        vlayout.add(header);

        currentDefaultDistSetTypeId = getCurrentDistributionSetTypeId();

        final HorizontalLayout hlayout = new HorizontalLayout();
        hlayout.setSpacing(true);

        final Span configurationLabel = new LabelBuilder()
                .name(i18n.getMessage("configuration.defaultdistributionset.select.label")).buildLabel();
        hlayout.add(configurationLabel);

        initDsSetTypeComboBox();
        hlayout.add(dsSetTypeComboBox);

        changeIcon = new Span(VaadinIcon.CHECK.create());
        hlayout.add(changeIcon);
        changeIcon.setVisible(false);

        vlayout.add(hlayout);
        rootPanel.add(vlayout);
        getContent().add(rootPanel);
    }

    private void initDsSetTypeComboBox() {
        dsSetTypeComboBox = builder.createDistributionSetTypeCombo(getBinder());
        dsSetTypeComboBox.addValueChangeListener(this::selectDistributionSetTypeValue);
    }

    private Long getCurrentDistributionSetTypeId() {
        return getBinderBean().getDsTypeInfo().getId();
    }

    /**
     * Method that is called when combobox event is performed.
     *
     * @param event
     *            ValueChangeEvent
     */
    private void selectDistributionSetTypeValue(final HasValue.ValueChangeEvent<ProxyTypeInfo> event) {
        changeIcon.setVisible(!event.getValue().getId().equals(currentDefaultDistSetTypeId));
    }

    @Override
    protected ProxySystemConfigDsType populateSystemConfig() {
        final ProxySystemConfigDsType configBean = new ProxySystemConfigDsType();
        configBean.setDsTypeInfo(new TypeToTypeInfoMapper<DistributionSetType>()
                .map(systemManagement.getTenantMetadata().getDefaultDsType()));
        return configBean;
    }

    @Override
    public void save() {
        systemManagement.updateTenantMetadata(getBinderBean().getDsTypeInfo().getId());
    }
}
