/**
 * Copyright (c) 2020 Bosch.IO GmbH and others
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.hawkbit.ui.filtermanagement;

import org.eclipse.hawkbit.repository.DistributionSetManagement;
import org.eclipse.hawkbit.ui.common.AbstractEntityWindowLayout;
import org.eclipse.hawkbit.ui.common.builder.BoundComponent;
import org.eclipse.hawkbit.ui.common.data.mappers.DistributionSetToProxyDistributionMapper;
import org.eclipse.hawkbit.ui.common.data.providers.DistributionSetStatelessDataProvider;
import org.eclipse.hawkbit.ui.common.data.proxies.ProxyDistributionSet;
import org.eclipse.hawkbit.ui.common.data.proxies.ProxyTargetFilterQuery;
import org.eclipse.hawkbit.ui.management.miscs.ActionTypeOptionGroupAutoAssignmentLayout;
import org.eclipse.hawkbit.ui.utils.VaadinMessageSource;

import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import org.eclipse.hawkbit.utils.TenantConfigHelper;

/**
 * Target add/update window layout.
 */
public class AutoAssignmentWindowLayout extends AbstractEntityWindowLayout<ProxyTargetFilterQuery> {
    private final AutoAssignmentWindowLayoutComponentBuilder autoAssignComponentBuilder;

    private final Span descriptionLabel;
    private final Checkbox enableCheckBox;
    private final Checkbox confirmationCheckBox;
    private final ActionTypeOptionGroupAutoAssignmentLayout actionTypeOptionGroupLayout;
    private final BoundComponent<ComboBox<ProxyDistributionSet>> autoAssignDsComboBox;
    
    private final TenantConfigHelper configHelper;
    
    /**
     * Constructor for AbstractTagWindowLayout
     * 
     * @param i18n
     *            VaadinMessageSource
     * @param dsManagement
     *            DistributionSetManagement
     * @param configHelper
     *            TenantConfigHelper
     */
    public AutoAssignmentWindowLayout(final VaadinMessageSource i18n, final DistributionSetManagement dsManagement,
            final TenantConfigHelper configHelper) {
        super();

        this.autoAssignComponentBuilder = new AutoAssignmentWindowLayoutComponentBuilder(i18n);

        this.descriptionLabel = autoAssignComponentBuilder.createDescriptionLabel();
        this.enableCheckBox = autoAssignComponentBuilder.createEnableCheckbox(binder);
        this.confirmationCheckBox = autoAssignComponentBuilder.createConfirmationCheckbox(binder);
        this.actionTypeOptionGroupLayout = autoAssignComponentBuilder.createActionTypeOptionGroupLayout(binder);
        this.autoAssignDsComboBox = autoAssignComponentBuilder.createDistributionSetCombo(binder,
                new DistributionSetStatelessDataProvider(dsManagement, new DistributionSetToProxyDistributionMapper()));
        this.configHelper = configHelper;

        addValueChangeListeners();
    }

    @Override
    public Component getRootComponent() {
        final VerticalLayout autoAssignmentLayout = new VerticalLayout();
        autoAssignmentLayout.setSpacing(true);
        autoAssignmentLayout.setPadding(true);

        autoAssignmentLayout.add(descriptionLabel);
        autoAssignmentLayout.add(enableCheckBox);
        autoAssignmentLayout.add(actionTypeOptionGroupLayout);
        autoAssignmentLayout.add(autoAssignDsComboBox.getComponent());
        
        if (configHelper.isConfirmationFlowEnabled()) {
            autoAssignmentLayout.add(confirmationCheckBox);
        }
        
        return autoAssignmentLayout;
    }

    private void addValueChangeListeners() {
        enableCheckBox.addValueChangeListener(event -> switchAutoAssignmentInputsVisibility(event.getValue()));
    }

    /**
     * Toggle auto assign input option
     *
     * @param autoAssignmentEnabled
     *          boolean
     */
    public void switchAutoAssignmentInputsVisibility(final boolean autoAssignmentEnabled) {
        actionTypeOptionGroupLayout.setVisible(autoAssignmentEnabled);
        autoAssignDsComboBox.getComponent().setVisible(autoAssignmentEnabled);
        autoAssignDsComboBox.setRequired(autoAssignmentEnabled);
        confirmationCheckBox.setVisible(autoAssignmentEnabled);
    }
}
