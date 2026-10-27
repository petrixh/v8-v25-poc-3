/**
 * Copyright (c) 2018 Bosch Software Innovations GmbH and others
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.hawkbit.ui.tenantconfiguration;

import org.eclipse.hawkbit.repository.TenantConfigurationManagement;
import org.eclipse.hawkbit.tenancy.configuration.TenantConfigurationProperties.TenantConfigurationKey;
import org.eclipse.hawkbit.ui.UiProperties;
import org.eclipse.hawkbit.ui.common.builder.FormComponentBuilder;
import org.eclipse.hawkbit.ui.common.data.proxies.ProxySystemConfigRollout;
import org.eclipse.hawkbit.ui.components.SPUIComponentProvider;
import org.eclipse.hawkbit.ui.tenantconfiguration.rollout.ApprovalConfigurationItem;
import org.eclipse.hawkbit.ui.utils.UIComponentIdProvider;
import org.eclipse.hawkbit.ui.utils.VaadinMessageSource;

import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;

/**
 * Provides configuration of the RolloutManagement including enabling/disabling
 * of the approval workflow.
 */
public class RolloutConfigurationView extends BaseConfigurationView<ProxySystemConfigRollout> {

    private static final long serialVersionUID = 1L;

    private final VaadinMessageSource i18n;
    private final UiProperties uiProperties;
    private final ApprovalConfigurationItem approvalConfigurationItem;

    RolloutConfigurationView(final VaadinMessageSource i18n, final UiProperties uiProperties,
            final TenantConfigurationManagement tenantConfigurationManagement) {
        super(tenantConfigurationManagement);
        this.i18n = i18n;
        this.uiProperties = uiProperties;
        this.approvalConfigurationItem = new ApprovalConfigurationItem(i18n);
    }

    @Override
    public void afterPropertiesSet() {
        super.afterPropertiesSet();
        init();
    }

    private void init() {
        final Div rootPanel = new Div();
        rootPanel.setSizeFull();
        rootPanel.addClassNames("config-panel");

        final VerticalLayout vLayout = new VerticalLayout();
        vLayout.setSpacing(false);
        vLayout.setPadding(true);
        vLayout.setSizeFull();

        final Span header = new Span(i18n.getMessage("configuration.rollout.title"));
        header.addClassNames("config-panel-header");
        vLayout.add(header);

        final Div gridLayout = createGridLayout();
        gridLayout.setSizeFull();

        final Checkbox approvalCheckbox = FormComponentBuilder.createCheckbox(
                UIComponentIdProvider.ROLLOUT_APPROVAL_ENABLED_CHECKBOX, getBinder(),
                ProxySystemConfigRollout::isRolloutApproval, ProxySystemConfigRollout::setRolloutApproval);

        addToGridLayout(gridLayout, approvalCheckbox, 0, 0);
        addToGridLayout(gridLayout, approvalConfigurationItem, 1, 0);
        final Anchor linkToApprovalHelp = SPUIComponentProvider.getHelpLink(i18n,
                uiProperties.getLinks().getDocumentation().getRollout());
        addToGridLayout(gridLayout, linkToApprovalHelp, 2, 0);
        alignBottomRight(linkToApprovalHelp);

        vLayout.add(gridLayout);
        rootPanel.add(vLayout);
        getContent().add(rootPanel);
    }

    @Override
    protected ProxySystemConfigRollout populateSystemConfig() {
        ProxySystemConfigRollout configBean = new ProxySystemConfigRollout();
        configBean.setRolloutApproval(readConfigOption(TenantConfigurationKey.ROLLOUT_APPROVAL_ENABLED));
        return configBean;
    }

    @Override
    public void save() {
        writeConfigOption(TenantConfigurationKey.ROLLOUT_APPROVAL_ENABLED, getBinderBean().isRolloutApproval());
    }

}
