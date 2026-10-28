/**
 * Copyright (c) 2015 Bosch Software Innovations GmbH and others
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.hawkbit.ui.login;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.eclipse.hawkbit.im.authentication.MultitenancyIndicator;
import org.eclipse.hawkbit.im.authentication.TenantUserPasswordAuthenticationToken;
import org.eclipse.hawkbit.ui.AbstractHawkbitUI;
import org.eclipse.hawkbit.ui.UiProperties;
import org.eclipse.hawkbit.ui.common.data.proxies.ProxyLoginCredentials;
import org.eclipse.hawkbit.ui.common.notification.ParallelNotification;
import org.eclipse.hawkbit.ui.components.SPUIComponentProvider;
import org.eclipse.hawkbit.ui.themes.HawkbitTheme;
import org.eclipse.hawkbit.ui.utils.HawkbitCommonUtil;
import org.eclipse.hawkbit.ui.utils.SPUIStyleDefinitions;
import org.eclipse.hawkbit.ui.utils.UIComponentIdProvider;
import org.eclipse.hawkbit.ui.utils.UINotification;
import org.eclipse.hawkbit.ui.utils.VaadinMessageSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.core.io.Resource;
import org.springframework.security.authentication.CredentialsExpiredException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StreamUtils;
import org.springframework.util.StringUtils;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.Html;
import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.NativeLabel;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.FlexComponent.Alignment;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.textfield.TextFieldVariant;
import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.HasDynamicTitle;
import com.vaadin.flow.server.VaadinSession;
import com.vaadin.flow.server.WebBrowser;

/**
 * Login UI window that is independent of the {@link AbstractHawkbitUI} itself.
 *
 * Vaadin 25: a route of its own ({@code /UI/login}, see the runtime's
 * subclass) outside the hawkBit UI layout; the Vaadin 8 UI init runs when the
 * route is entered.
 */
public abstract class AbstractHawkbitLoginUI extends VerticalLayout implements BeforeEnterObserver, HasDynamicTitle {
    private static final Logger LOG = LoggerFactory.getLogger(AbstractHawkbitLoginUI.class);

    private static final long serialVersionUID = 1L;

    private static final String TITLE = "hawkBit UI - Login";

    private static final String USER_PARAMETER = "user";
    private static final String TENANT_PARAMETER = "tenant";
    private static final String LOGIN_TEXTFIELD = "login-textfield";
    private static final String TEXTFIELD_INLINE_ICON = "inline-icon";

    private final transient ApplicationContext context;

    private final transient VaadinLoginSupport vaadinSecurity;

    private final VaadinMessageSource i18n;

    private final UiProperties uiProperties;

    private final transient MultitenancyIndicator multiTenancyIndicator;

    private final boolean isDemo;

    private TextField username;
    private TextField tenant;
    private PasswordField password;
    private Button signIn;

    private MultiValueMap<String, String> params;

    private Binder<ProxyLoginCredentials> binder;

    @Autowired
    protected AbstractHawkbitLoginUI(final ApplicationContext context, final VaadinLoginSupport vaadinSecurity,
            final VaadinMessageSource i18n, final UiProperties uiProperties,
            final MultitenancyIndicator multiTenancyIndicator) {
        this.context = context;
        this.vaadinSecurity = vaadinSecurity;
        this.i18n = i18n;
        this.uiProperties = uiProperties;
        this.multiTenancyIndicator = multiTenancyIndicator;
        this.isDemo = !uiProperties.getDemo().getDisclaimer().isEmpty();
    }

    @Override
    public String getPageTitle() {
        return TITLE;
    }

    @Override
    public void beforeEnter(final BeforeEnterEvent event) {
        init(event);
    }

    protected void init(final BeforeEnterEvent event) {
        HawkbitCommonUtil.initLocalization(UI.getCurrent(), uiProperties.getLocalization(), i18n);

        params = new LinkedMultiValueMap<>(event.getLocation().getQueryParameters().getParameters());

        removeAll();
        // Vaadin 8 ran init once per UI; Flow reuses this route instance when
        // the login view is entered again in the same UI, so the fields built
        // below get a binder of their own (no bindings or listeners kept from
        // the fields removed above)
        binder = new Binder<>();
        buildContent();

        final ProxyLoginCredentials credentialsBean = new ProxyLoginCredentials();
        populateCredentials(credentialsBean);

        binder.addStatusChangeListener(statusEvent -> signIn.setEnabled(statusEvent.getBinder().isValid()));
        binder.setBean(credentialsBean);

        focusSignIn();
    }

    /**
     * Vaadin 8 opened the login page with the focus on the Sign in button, though the button is disabled until both
     * fields are filled. A disabled Flow button cannot take focus, so it is focused while enabled and disabled in the
     * round trip after (the focus stays).
     */
    private void focusSignIn() {
        if (isUnsupportedBrowser()) {
            return;
        }
        final Binder<ProxyLoginCredentials> currentBinder = binder;
        signIn.setEnabled(true);
        signIn.getElement().executeJs(
                "return customElements.whenDefined('vaadin-button').then(() => this.updateComplete)"
                        + ".then(() => new Promise(resolve => setTimeout(() => { this.focus(); resolve(); }, 0)));")
                .then(result -> signIn.setEnabled(currentBinder.isValid()));
    }

    private void buildContent() {
        final VerticalLayout rootLayout = this;
        rootLayout.setSpacing(false);
        rootLayout.setPadding(false);
        rootLayout.setSizeFull();
        rootLayout.setClassName("main-content");

        rootLayout.add(buildHeader());

        addLoginForm(rootLayout);

        addFooter(rootLayout);
    }

    private static Component buildHeader() {
        final Div cssLayout = new Div();
        cssLayout.setClassName("view-header");
        return cssLayout;
    }

    private void addLoginForm(final VerticalLayout rootLayout) {
        final Component loginForm = buildLoginForm();
        rootLayout.add(loginForm);
        rootLayout.setHorizontalComponentAlignment(Alignment.CENTER, loginForm);
    }

    protected Component buildLoginForm() {
        final VerticalLayout loginPanel = new VerticalLayout();
        loginPanel.setPadding(false);
        loginPanel.setSpacing(true);
        loginPanel.setSizeUndefined();
        loginPanel.addClassName("login-panel");

        loginPanel.add(buildFields());
        if (isDemo) {
            loginPanel.add(buildDisclaimer());
        }
        loginPanel.add(buildLinks());

        checkBrowserSupport(loginPanel);

        return loginPanel;
    }

    protected Component buildFields() {
        final HorizontalLayout fields = new HorizontalLayout();
        fields.setPadding(false);
        fields.setSpacing(true);
        fields.addClassName("fields");

        buildTenantField();
        buildUserField();
        buildPasswordField();
        buildSignInButton();

        if (multiTenancyIndicator.isMultiTenancySupported()) {
            fields.add(tenant, username, password, signIn);
        } else {
            fields.add(username, password, signIn);
        }

        fields.setVerticalComponentAlignment(Alignment.END, signIn);

        return fields;
    }

    private void buildTenantField() {
        if (multiTenancyIndicator.isMultiTenancySupported()) {
            tenant = new TextField(i18n.getMessage("label.login.tenant"));
            tenant.setPrefixComponent(VaadinIcon.DATABASE.create());
            tenant.addThemeVariants(TextFieldVariant.LUMO_SMALL);
            tenant.addClassNames(TEXTFIELD_INLINE_ICON, LOGIN_TEXTFIELD);
            tenant.addClassName("uppercase");
            tenant.setId("login-tenant");
            tenant.setValueChangeMode(ValueChangeMode.LAZY);

            binder.forField(tenant).asRequired().bind(ProxyLoginCredentials::getTenant,
                    ProxyLoginCredentials::setTenant);
        }
    }

    private void buildUserField() {
        username = new TextField(i18n.getMessage("label.login.username"));
        username.setPrefixComponent(VaadinIcon.USER.create());
        username.addThemeVariants(TextFieldVariant.LUMO_SMALL);
        username.addClassNames(TEXTFIELD_INLINE_ICON, LOGIN_TEXTFIELD);
        username.setId("login-username");
        // Vaadin 8's default (also on tenant and password): the sign-in button follows the binder while typing,
        // not only when the field loses focus
        username.setValueChangeMode(ValueChangeMode.LAZY);

        binder.forField(username).asRequired().bind(ProxyLoginCredentials::getUsername,
                ProxyLoginCredentials::setUsername);
    }

    private void buildPasswordField() {
        password = new PasswordField(i18n.getMessage("label.login.password"));
        password.setPrefixComponent(VaadinIcon.LOCK.create());
        password.addThemeVariants(TextFieldVariant.LUMO_SMALL);
        password.addClassNames(TEXTFIELD_INLINE_ICON, LOGIN_TEXTFIELD);
        password.setId("login-password");
        // Vaadin 8's password field had no reveal button: hidden for parity
        password.setRevealButtonVisible(false);
        password.setValueChangeMode(ValueChangeMode.LAZY);

        binder.forField(password).asRequired().bind(ProxyLoginCredentials::getPassword,
                ProxyLoginCredentials::setPassword);
    }

    private void buildSignInButton() {
        final String caption = isDemo ? i18n.getMessage("button.login.agreeandsignin")
                : i18n.getMessage("button.login.signin");

        signIn = new Button(caption);
        signIn.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SMALL);
        signIn.addClassName("login-button");
        signIn.addClickShortcut(Key.ENTER);
        signIn.setId("login-signin");

        signIn.addClickListener(event -> handleLogin());
    }

    private void handleLogin() {
        final ProxyLoginCredentials credentialsBean = binder.getBean();
        final String providedTenant = credentialsBean.getTenant();
        final String providedUsername = credentialsBean.getUsername();
        final String providedPassword = credentialsBean.getPassword();

        if (multiTenancyIndicator.isMultiTenancySupported()) {
            if (StringUtils.hasLength(providedTenant) && StringUtils.hasLength(providedUsername)
                    && StringUtils.hasLength(providedPassword)) {
                login(providedTenant, providedUsername, providedPassword);
            }
        } else if (!multiTenancyIndicator.isMultiTenancySupported() && StringUtils.hasLength(providedUsername)
                && StringUtils.hasLength(providedPassword)) {
            login(null, providedUsername, providedPassword);
        }
    }

    private void login(final String tenant, final String user, final String password) {
        try {
            if (multiTenancyIndicator.isMultiTenancySupported()) {
                vaadinSecurity.login(new TenantUserPasswordAuthenticationToken(tenant, user, password));
            } else {
                vaadinSecurity.login(new UsernamePasswordAuthenticationToken(user, password));
            }
        } catch (final CredentialsExpiredException e) {
            LOG.debug("Credential expired", e);
            loginCredentialsExpiredNotification();
        } catch (final AuthenticationException e) {
            LOG.debug("Authentication failed", e);
            /* if not successful */
            loginAuthenticationFailedNotification();
        } catch (final Exception e) {
            LOG.debug("Login failed", e);
            loginAuthenticationFailedNotification();
        }
    }

    private void loginCredentialsExpiredNotification() {
        showErrorNotification(i18n.getMessage("notification.login.failed.credentialsexpired.title"),
                i18n.getMessage("notification.login.failed.credentialsexpired.description"));
    }

    private void showErrorNotification(final String caption, final String description) {
        final ParallelNotification notification = UINotification.buildNotification(
                SPUIStyleDefinitions.SP_NOTIFICATION_ERROR_MESSAGE_STYLE, caption, description, null, true);
        notification.setPosition(Notification.Position.BOTTOM_CENTER);
        notification.open();
    }

    private void loginAuthenticationFailedNotification() {
        showErrorNotification(i18n.getMessage("notification.login.failed.title"),
                i18n.getMessage("notification.login.failed.description"));
    }

    private Component buildDisclaimer() {
        final HorizontalLayout fields = new HorizontalLayout();
        fields.setPadding(false);
        fields.setSpacing(true);
        fields.addClassName("disclaimer");

        // Vaadin 8: an HTML label with caption and icon; the caption and icon
        // go above the text
        final NativeLabel caption = new NativeLabel(i18n.getMessage("label.login.disclaimer"));
        final Div captionWithIcon = new Div(VaadinIcon.EXCLAMATION_CIRCLE.create(), caption);
        final Span disclaimer = new Span();
        disclaimer.getElement().setProperty("innerHTML", uiProperties.getDemo().getDisclaimer());
        disclaimer.setId("login-disclaimer");
        disclaimer.setWidth("525px");

        fields.add(new Div(captionWithIcon, disclaimer));

        return fields;
    }

    protected Component buildLinks() {
        final HorizontalLayout links = new HorizontalLayout();
        links.setPadding(false);
        links.setSpacing(true);
        links.addClassName("links");
        final String linkStyle = "v-link";

        if (!uiProperties.getLinks().getDocumentation().getRoot().isEmpty()) {
            final Anchor docuLink = SPUIComponentProvider.getLink(UIComponentIdProvider.LINK_DOCUMENTATION,
                    i18n.getMessage("link.documentation.name"), uiProperties.getLinks().getDocumentation().getRoot(),
                    VaadinIcon.QUESTION_CIRCLE, "_blank", linkStyle);
            links.add(docuLink);
            docuLink.addClassName("small");
        }

        if (!uiProperties.getLinks().getRequestAccount().isEmpty()) {
            final Anchor requestAccountLink = SPUIComponentProvider.getLink(UIComponentIdProvider.LINK_REQUESTACCOUNT,
                    i18n.getMessage("link.requestaccount.name"), uiProperties.getLinks().getRequestAccount(),
                    VaadinIcon.CART, "", linkStyle);
            links.add(requestAccountLink);
            requestAccountLink.addClassName("small");
        }

        if (!uiProperties.getLinks().getUserManagement().isEmpty()) {
            final Anchor userManagementLink = SPUIComponentProvider.getLink(UIComponentIdProvider.LINK_USERMANAGEMENT,
                    i18n.getMessage("link.usermanagement.name"), uiProperties.getLinks().getUserManagement(),
                    VaadinIcon.USERS, "_blank", linkStyle);
            links.add(userManagementLink);
            userManagementLink.addClassName("small");
        }

        return links;
    }

    protected void checkBrowserSupport(final VerticalLayout loginPanel) {
        // Check if IE browser is not supported ( < IE11 )
        if (isUnsupportedBrowser()) {
            // Disable sign-in button and display a message
            signIn.setEnabled(Boolean.FALSE);
            loginPanel.add(buildUnsupportedMessage());
        }
    }

    private static boolean isUnsupportedBrowser() {
        final WebBrowser webBrowser = VaadinSession.getCurrent().getBrowser();
        return webBrowser.isIE() && webBrowser.getBrowserMajorVersion() < 11;
    }

    private Component buildUnsupportedMessage() {
        final Span label = new Span(i18n.getMessage("label.unsupported.browser.ie"));
        label.addClassName("failure");
        return label;
    }

    private void addFooter(final VerticalLayout rootLayout) {
        final Resource resource = context
                .getResource("classpath:/VAADIN/themes/" + HawkbitTheme.THEME_NAME + "/layouts/footer.html");

        // Vaadin 8 CustomLayout: the template as it is, inside one element
        try (final InputStream resourceStream = resource.getInputStream()) {
            final Html customLayout = new Html(
                    "<div>" + StreamUtils.copyToString(resourceStream, StandardCharsets.UTF_8) + "</div>");
            rootLayout.add(customLayout);
            rootLayout.setHorizontalComponentAlignment(Alignment.START, customLayout);
        } catch (final IOException ex) {
            LOG.error("Footer file cannot be loaded", ex);
        }
    }

    private void populateCredentials(final ProxyLoginCredentials credentialsBean) {
        if (tenant != null && params.containsKey(TENANT_PARAMETER) && !params.get(TENANT_PARAMETER).isEmpty()) {
            credentialsBean.setTenant(params.get(TENANT_PARAMETER).get(0));
            tenant.setVisible(false);
        }

        if (params.containsKey(USER_PARAMETER) && !params.get(USER_PARAMETER).isEmpty()) {
            credentialsBean.setUsername(params.get(USER_PARAMETER).get(0));
        } else if (isDemo && !uiProperties.getDemo().getUser().isEmpty()) {
            credentialsBean.setUsername(uiProperties.getDemo().getUser());
        }

        if (isDemo && !uiProperties.getDemo().getPassword().isEmpty()) {
            credentialsBean.setPassword(uiProperties.getDemo().getPassword());
        }
    }

    protected MultiValueMap<String, String> getParams() {
        return params;
    }

    protected TextField getUsername() {
        return username;
    }

    protected TextField getTenant() {
        return tenant;
    }

    protected PasswordField getPassword() {
        return password;
    }

    protected VaadinMessageSource getI18n() {
        return i18n;
    }
}
