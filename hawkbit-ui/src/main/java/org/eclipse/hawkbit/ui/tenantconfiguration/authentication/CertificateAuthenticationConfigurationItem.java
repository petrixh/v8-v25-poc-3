/**
 * Copyright (c) 2015 Bosch Software Innovations GmbH and others
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.hawkbit.ui.tenantconfiguration.authentication;

import org.eclipse.hawkbit.repository.model.TenantConfiguration;
import org.eclipse.hawkbit.ui.common.builder.LabelBuilder;
import org.eclipse.hawkbit.ui.common.builder.TextFieldBuilder;
import org.eclipse.hawkbit.ui.common.data.proxies.ProxySystemConfigAuthentication;
import org.eclipse.hawkbit.ui.components.SPUIComponentProvider;
import org.eclipse.hawkbit.ui.utils.VaadinMessageSource;

import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;

/**
 * This class represents the UI item for the certificate authenticated by an
 * reverse proxy in the authentication configuration view.
 */
public class CertificateAuthenticationConfigurationItem extends VerticalLayout {

    private static final long serialVersionUID = 1L;

    private final VerticalLayout detailLayout;
    private final TextField caRootAuthorityTextField;

    /**
     * Constructor for CertificateAuthenticationConfigurationItem
     *
     * @param i18n
     *            VaadinMessageSource
     * @param binder
     *            System config window binder
     */
    public CertificateAuthenticationConfigurationItem(final VaadinMessageSource i18n,
            final Binder<ProxySystemConfigAuthentication> binder) {
        this.setSpacing(false);
        this.setPadding(false);
        add(SPUIComponentProvider.generateLabel(i18n, "label.configuration.auth.header"));
        detailLayout = new VerticalLayout();
        detailLayout.setPadding(false);
        detailLayout.setSpacing(false);

        final HorizontalLayout caRootAuthorityLayout = new HorizontalLayout();
        caRootAuthorityLayout.setSpacing(true);

        final Span caRootAuthorityLabel = new LabelBuilder()
                .name(i18n.getMessage("label.configuration.auth.hashField")).buildLabel();
        caRootAuthorityLabel.setDescription(i18n.getMessage("label.configuration.auth.hashField.tooltip"));
        caRootAuthorityLabel.setWidth(null);

        caRootAuthorityTextField = new TextFieldBuilder(TenantConfiguration.VALUE_MAX_SIZE).buildTextComponent();
        caRootAuthorityTextField.setWidth("100%");
        binder.bind(caRootAuthorityTextField, ProxySystemConfigAuthentication::getCaRootAuthority,
                ProxySystemConfigAuthentication::setCaRootAuthority);
        caRootAuthorityLayout.add(caRootAuthorityLabel);
        caRootAuthorityLayout.setFlexGrow(0, caRootAuthorityLabel);
        caRootAuthorityLayout.add(caRootAuthorityTextField);
        caRootAuthorityLayout.setFlexGrow(1, caRootAuthorityTextField);
        caRootAuthorityLayout.setWidth("100%");
        detailLayout.add(caRootAuthorityLayout);
        if (binder.getBean().isCertificateAuth()) {
            showDetails();
        }
    }

    /**
     * Show Certificate detail
     */
    public void showDetails() {
        add(detailLayout);
    }

    /**
     * Hide Certificate detail
     */
    public void hideDetails() {
        remove(detailLayout);
    }
}
