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

import org.eclipse.hawkbit.security.SecurityTokenGenerator;
import org.eclipse.hawkbit.ui.common.builder.LabelBuilder;
import org.eclipse.hawkbit.ui.common.data.proxies.ProxySystemConfigAuthentication;
import org.eclipse.hawkbit.ui.components.SPUIComponentProvider;
import org.eclipse.hawkbit.ui.decorators.SPUIButtonStyleSmall;
import org.eclipse.hawkbit.ui.utils.VaadinMessageSource;

import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.data.binder.ReadOnlyHasValue;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;

/**
 * This class represents the UI item for the gateway security token section in
 * the authentication configuration view.
 */
public class GatewaySecurityTokenAuthenticationConfigurationItem extends VerticalLayout {

    private static final long serialVersionUID = 1L;

    private final transient SecurityTokenGenerator securityTokenGenerator;
    private final VerticalLayout detailLayout;
    private final Binder<ProxySystemConfigAuthentication> binder;

    /**
     * Constructor for GatewaySecurityTokenAuthenticationConfigurationItem
     *
     * @param i18n
     *            VaadinMessageSource
     * @param securityTokenGenerator
     *            SecurityTokenGenerator
     * @param binder
     *            System config window binder
     */
    public GatewaySecurityTokenAuthenticationConfigurationItem(final VaadinMessageSource i18n,
            final SecurityTokenGenerator securityTokenGenerator, final Binder<ProxySystemConfigAuthentication> binder) {
        this.securityTokenGenerator = securityTokenGenerator;
        this.binder = binder;
        this.setSpacing(false);
        this.setPadding(false);
        add(SPUIComponentProvider.generateLabel(i18n, "label.configuration.auth.gatewaytoken"));

        detailLayout = new VerticalLayout();
        detailLayout.setPadding(false);
        detailLayout.setSpacing(false);

        final Button gatewaytokenBtn = SPUIComponentProvider.getButton(null,
                i18n.getMessage("configuration.button.regenerateKey"), "", "tiny" + " " + "redicon",
                true, null, SPUIButtonStyleSmall.class);

        gatewaytokenBtn.setIcon(VaadinIcon.REFRESH);
        gatewaytokenBtn.addClickListener(event -> refreshGatewayToken());

        final Span gatewayTokenLabel = new LabelBuilder().id("gatewaysecuritytokenkey").name("").buildLabel();
        gatewayTokenLabel.addClassNames("gateway-token-label");
        final ReadOnlyHasValue<String> gatewayTokenFieldBindable = new ReadOnlyHasValue<>(gatewayTokenLabel::setValue);
        binder.bind(gatewayTokenFieldBindable, ProxySystemConfigAuthentication::getGatewaySecurityToken, null);

        final HorizontalLayout keyGenerationLayout = new HorizontalLayout();
        keyGenerationLayout.setSpacing(true);
        keyGenerationLayout.add(gatewayTokenLabel);
        keyGenerationLayout.add(gatewaytokenBtn);
        detailLayout.add(keyGenerationLayout);
        if (binder.getBean().isGatewaySecToken()) {
            showDetails();
        }
    }

    /**
     * Show gateway token detail
     */
    public void showDetails() {
        add(detailLayout);
    }

    /**
     * Hide gateway token detail
     */
    public void hideDetails() {
        remove(detailLayout);
    }

    private void refreshGatewayToken() {
        binder.getBean().setGatewaySecurityToken(securityTokenGenerator.generateToken());
        binder.setBean(binder.getBean());
    }
}
