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

import org.eclipse.hawkbit.repository.SoftwareModuleTypeManagement;
import org.eclipse.hawkbit.ui.common.AbstractEntityWindowLayout;
import org.eclipse.hawkbit.ui.common.data.mappers.TypeToTypeInfoMapper;
import org.eclipse.hawkbit.ui.common.data.providers.SoftwareModuleTypeDataProvider;
import org.eclipse.hawkbit.ui.common.data.proxies.ProxySoftwareModule;
import org.eclipse.hawkbit.ui.common.data.proxies.ProxyTypeInfo;
import org.eclipse.hawkbit.ui.utils.VaadinMessageSource;

import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.formlayout.FormLayout.ResponsiveStep;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;

/**
 * Target add/update window layout.
 */
public class SmWindowLayout extends AbstractEntityWindowLayout<ProxySoftwareModule> {
    private final SmWindowLayoutComponentBuilder smComponentBuilder;

    private final ComboBox<ProxyTypeInfo> smTypeSelect;
    private final TextField smName;
    private final TextField smVersion;
    private final TextField smVendor;
    private final TextArea smDescription;
    private final Checkbox artifactEncryption;

    /**
     * Constructor for AbstractTagWindowLayout
     * 
     * @param i18n
     *            VaadinMessageSource
     * @param smTypeManagement
     *            SoftwareModuleTypeManagement
     */
    public SmWindowLayout(final VaadinMessageSource i18n, final SoftwareModuleTypeManagement smTypeManagement) {
        super();

        final SoftwareModuleTypeDataProvider<ProxyTypeInfo> smTypeDataProvider = new SoftwareModuleTypeDataProvider<>(
                smTypeManagement, new TypeToTypeInfoMapper<>());
        this.smComponentBuilder = new SmWindowLayoutComponentBuilder(i18n, smTypeDataProvider);

        this.smTypeSelect = smComponentBuilder.createSoftwareModuleTypeCombo(binder);
        this.smName = smComponentBuilder.createNameField(binder);
        this.smVersion = smComponentBuilder.createVersionField(binder);
        this.smVendor = smComponentBuilder.createVendorField(binder);
        this.smDescription = smComponentBuilder.createDescription(binder);
        this.artifactEncryption = smComponentBuilder.createArtifactEncryptionCheck(binder);
    }

    /**
     * @return software module window layout
     */
    @Override
    public Component getRootComponent() {
        final FormLayout smWindowLayout = new FormLayout();

        // Vaadin 8 FormLayout: one column; Flow has no spacing/margin here
        smWindowLayout.setResponsiveSteps(new ResponsiveStep("0", 1));
        smWindowLayout.setSizeUndefined();

        smWindowLayout.add(smTypeSelect);

        smWindowLayout.add(smName);
        smName.focus();

        smWindowLayout.add(smVersion);

        smWindowLayout.add(smVendor);

        smWindowLayout.add(smDescription);

        smWindowLayout.add(artifactEncryption);

        return smWindowLayout;
    }

    /**
     * Disable the software module type
     */
    public void disableSmTypeSelect() {
        smTypeSelect.setEnabled(false);
    }

    /**
     * Disable the software module name
     */
    public void disableNameField() {
        smName.setEnabled(false);
    }

    /**
     * Disable the software module version
     */
    public void disableVersionField() {
        smVersion.setEnabled(false);
    }

    /**
     * Disable the software module artifact encryption
     */
    public void disableEncryptionField() {
        artifactEncryption.setEnabled(false);
    }
}
