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

import org.eclipse.hawkbit.repository.TenantConfigurationManagement;
import org.eclipse.hawkbit.security.SecurityTokenGenerator;
import org.eclipse.hawkbit.tenancy.configuration.TenantConfigurationProperties.TenantConfigurationKey;
import org.eclipse.hawkbit.ui.UiProperties;
import org.eclipse.hawkbit.ui.common.builder.FormComponentBuilder;
import org.eclipse.hawkbit.ui.common.data.proxies.ProxySystemConfigAuthentication;
import org.eclipse.hawkbit.ui.components.SPUIComponentProvider;
import org.eclipse.hawkbit.ui.tenantconfiguration.authentication.AnonymousDownloadAuthenticationConfigurationItem;
import org.eclipse.hawkbit.ui.tenantconfiguration.authentication.CertificateAuthenticationConfigurationItem;
import org.eclipse.hawkbit.ui.tenantconfiguration.authentication.GatewaySecurityTokenAuthenticationConfigurationItem;
import org.eclipse.hawkbit.ui.tenantconfiguration.authentication.TargetSecurityTokenAuthenticationConfigurationItem;
import org.eclipse.hawkbit.ui.utils.UIComponentIdProvider;
import org.eclipse.hawkbit.ui.utils.VaadinMessageSource;

import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import org.springframework.util.StringUtils;

import static org.eclipse.hawkbit.ui.utils.SPUIStyleDefinitions.DIST_CHECKBOX_STYLE;

/**
 * View to configure the authentication mode.
 */
public class AuthenticationConfigurationView extends BaseConfigurationView<ProxySystemConfigAuthentication> {

    private static final long serialVersionUID = 1L;

    private final VaadinMessageSource i18n;

    private final transient SecurityTokenGenerator securityTokenGenerator;
    private CertificateAuthenticationConfigurationItem certificateAuthenticationConfigurationItem;
    private TargetSecurityTokenAuthenticationConfigurationItem targetSecurityTokenAuthenticationConfigurationItem;
    private GatewaySecurityTokenAuthenticationConfigurationItem gatewaySecurityTokenAuthenticationConfigurationItem;
    private AnonymousDownloadAuthenticationConfigurationItem anonymousDownloadAuthenticationConfigurationItem;

    private final UiProperties uiProperties;

    public AuthenticationConfigurationView(final VaadinMessageSource i18n, final UiProperties uiProperties,
            final TenantConfigurationManagement tenantConfigurationManagement,
            final SecurityTokenGenerator securityTokenGenerator) {
        super(tenantConfigurationManagement);
        this.i18n = i18n;
        this.uiProperties = uiProperties;
        this.securityTokenGenerator = securityTokenGenerator;
    }

    @Override
    public void afterPropertiesSet() {
        super.afterPropertiesSet();
        this.targetSecurityTokenAuthenticationConfigurationItem = new TargetSecurityTokenAuthenticationConfigurationItem(
                i18n);
        this.certificateAuthenticationConfigurationItem = new CertificateAuthenticationConfigurationItem(i18n,
                getBinder());
        this.gatewaySecurityTokenAuthenticationConfigurationItem = new GatewaySecurityTokenAuthenticationConfigurationItem(
                i18n, securityTokenGenerator, getBinder());
        this.anonymousDownloadAuthenticationConfigurationItem = new AnonymousDownloadAuthenticationConfigurationItem(
                i18n);
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

        final Span header = new Span(i18n.getMessage("configuration.authentication.title"));
        header.addClassNames("config-panel-header");
        vLayout.add(header);

        final Div gridLayout = createGridLayout();

        gridLayout.setSizeFull();

        initCertificateAuthConfiguration(gridLayout, 0);
        initTargetTokenConfiguration(gridLayout, 1);
        initGatewayTokenConfiguration(gridLayout, 2);
        initAnonymousDownloadConfiguration(gridLayout, 3);

        vLayout.add(gridLayout);
        rootPanel.add(vLayout);
        getContent().add(rootPanel);
    }

    protected void initCertificateAuthConfiguration(Div gridLayout, int row) {
        final Checkbox certificateAuthCheckbox = FormComponentBuilder.createCheckbox(
                UIComponentIdProvider.CERT_AUTH_ALLOWED_CHECKBOX, getBinder(),
                ProxySystemConfigAuthentication::isCertificateAuth,
                ProxySystemConfigAuthentication::setCertificateAuth);
        certificateAuthCheckbox.setClassName(DIST_CHECKBOX_STYLE);
        certificateAuthCheckbox.addValueChangeListener(valueChangeEvent -> {
            if (valueChangeEvent.getValue()) {
                certificateAuthenticationConfigurationItem.showDetails();
            } else {
                certificateAuthenticationConfigurationItem.hideDetails();
            }
        });
        addToGridLayout(gridLayout, certificateAuthCheckbox, 0, row);
        addToGridLayout(gridLayout, certificateAuthenticationConfigurationItem, 1, row);
    }

    protected void initTargetTokenConfiguration(Div gridLayout, int row) {
        final Checkbox targetSecTokenCheckBox = FormComponentBuilder.createCheckbox(
                UIComponentIdProvider.TARGET_SEC_TOKEN_ALLOWED_CHECKBOX, getBinder(),
                ProxySystemConfigAuthentication::isTargetSecToken, ProxySystemConfigAuthentication::setTargetSecToken);
        targetSecTokenCheckBox.setClassName(DIST_CHECKBOX_STYLE);
        addToGridLayout(gridLayout, targetSecTokenCheckBox, 0, row);
        addToGridLayout(gridLayout, targetSecurityTokenAuthenticationConfigurationItem, 1, row);
    }

    protected void initGatewayTokenConfiguration(Div gridLayout, int row) {
        final Checkbox gatewaySecTokenCheckBox = FormComponentBuilder.createCheckbox(
                UIComponentIdProvider.GATEWAY_SEC_TOKEN_ALLOWED_CHECKBOX, getBinder(),
                ProxySystemConfigAuthentication::isGatewaySecToken,
                ProxySystemConfigAuthentication::setGatewaySecToken);
        gatewaySecTokenCheckBox.setClassName(DIST_CHECKBOX_STYLE);
        gatewaySecTokenCheckBox.addValueChangeListener(valueChangeEvent -> {
            if (valueChangeEvent.getValue()) {
                gatewaySecurityTokenAuthenticationConfigurationItem.showDetails();
            } else {
                gatewaySecurityTokenAuthenticationConfigurationItem.hideDetails();
            }
        });
        addToGridLayout(gridLayout, gatewaySecTokenCheckBox, 0, row);
        addToGridLayout(gridLayout, gatewaySecurityTokenAuthenticationConfigurationItem, 1, row);
    }

    protected void initAnonymousDownloadConfiguration(Div gridLayout, int row) {
        final Checkbox downloadAnonymousCheckBox = FormComponentBuilder.createCheckbox(
                UIComponentIdProvider.DOWNLOAD_ANONYMOUS_CHECKBOX, getBinder(),
                ProxySystemConfigAuthentication::isDownloadAnonymous,
                ProxySystemConfigAuthentication::setDownloadAnonymous);
        downloadAnonymousCheckBox.setClassName(DIST_CHECKBOX_STYLE);
        addToGridLayout(gridLayout, downloadAnonymousCheckBox, 0, row);
        addToGridLayout(gridLayout, anonymousDownloadAuthenticationConfigurationItem, 1, row);

        final Anchor linkToSecurityHelp = SPUIComponentProvider.getHelpLink(i18n,
                uiProperties.getLinks().getDocumentation().getSecurity());
        addToGridLayout(gridLayout, linkToSecurityHelp, 2, row);
        alignBottomRight(linkToSecurityHelp);
    }

    @Override
    public void save() {
        if (getBinderBean().isGatewaySecToken()) {
            writeConfigOption(TenantConfigurationKey.AUTHENTICATION_MODE_GATEWAY_SECURITY_TOKEN_KEY,
                    getBinderBean().getGatewaySecurityToken());
        }

        writeConfigOption(TenantConfigurationKey.AUTHENTICATION_MODE_TARGET_SECURITY_TOKEN_ENABLED,
                getBinderBean().isTargetSecToken());
        writeConfigOption(TenantConfigurationKey.AUTHENTICATION_MODE_GATEWAY_SECURITY_TOKEN_ENABLED,
                getBinderBean().isGatewaySecToken());
        writeConfigOption(TenantConfigurationKey.ANONYMOUS_DOWNLOAD_MODE_ENABLED,
                getBinderBean().isDownloadAnonymous());

        writeConfigOption(TenantConfigurationKey.AUTHENTICATION_MODE_HEADER_ENABLED,
                getBinderBean().isCertificateAuth());
        if (getBinderBean().isCertificateAuth()) {
            final String value = getBinderBean().getCaRootAuthority() != null ? getBinderBean().getCaRootAuthority()
                    : "";
            writeConfigOption(TenantConfigurationKey.AUTHENTICATION_MODE_HEADER_AUTHORITY_NAME, value);
        }
    }

    @Override
    protected ProxySystemConfigAuthentication populateSystemConfig() {
        final ProxySystemConfigAuthentication configBean = new ProxySystemConfigAuthentication();
        configBean.setCertificateAuth(readConfigOption(TenantConfigurationKey.AUTHENTICATION_MODE_HEADER_ENABLED));
        configBean.setTargetSecToken(
                readConfigOption(TenantConfigurationKey.AUTHENTICATION_MODE_TARGET_SECURITY_TOKEN_ENABLED));
        configBean.setGatewaySecToken(
                readConfigOption(TenantConfigurationKey.AUTHENTICATION_MODE_GATEWAY_SECURITY_TOKEN_ENABLED));
        configBean.setDownloadAnonymous(readConfigOption(TenantConfigurationKey.ANONYMOUS_DOWNLOAD_MODE_ENABLED));

        String securityToken = getTenantConfigurationManagement().getConfigurationValue(
                TenantConfigurationKey.AUTHENTICATION_MODE_GATEWAY_SECURITY_TOKEN_KEY, String.class).getValue();
        if (StringUtils.isEmpty(securityToken)) {
            securityToken = securityTokenGenerator.generateToken();
        }
        configBean.setGatewaySecurityToken(securityToken);
        configBean.setCaRootAuthority(getCaRootAuthorityValue());

        return configBean;
    }

    private String getCaRootAuthorityValue() {
        return getTenantConfigurationManagement()
                .getConfigurationValue(TenantConfigurationKey.AUTHENTICATION_MODE_HEADER_AUTHORITY_NAME, String.class)
                .getValue();
    }

}
