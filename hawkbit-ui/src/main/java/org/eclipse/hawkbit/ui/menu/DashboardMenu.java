/**
 * Copyright (c) 2015 Bosch Software Innovations GmbH and others
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.hawkbit.ui.menu;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.eclipse.hawkbit.HawkbitServerProperties;
import org.eclipse.hawkbit.im.authentication.PermissionService;
import org.eclipse.hawkbit.ui.UiProperties;
import org.eclipse.hawkbit.ui.common.UserDetailsFormatter;
import org.eclipse.hawkbit.ui.components.SPUIComponentProvider;
import org.eclipse.hawkbit.ui.menu.DashboardEvent.PostViewChangeEvent;
import org.eclipse.hawkbit.ui.utils.UIComponentIdProvider;
import org.eclipse.hawkbit.ui.utils.VaadinMessageSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import org.springframework.web.util.UriComponentsBuilder;

import com.vaadin.flow.component.ClickEvent;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.ComponentEventListener;
import com.vaadin.flow.component.Composite;
import com.vaadin.flow.component.HasSize;
import com.vaadin.flow.component.HasStyle;
import com.vaadin.flow.component.Text;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.contextmenu.MenuItem;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.menubar.MenuBar;
import com.vaadin.flow.component.orderedlayout.FlexComponent.Alignment;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.spring.annotation.SpringComponent;
import com.vaadin.flow.spring.annotation.UIScope;

/**
 * A responsive menu component providing user information and the controls for
 * primary navigation between the views.
 */
@UIScope
@SpringComponent
public final class DashboardMenu extends Composite<Div> implements HasStyle, HasSize {

    private static final String ID = "dashboard-menu";

    private static final String LOGOUT_BASE = "/UI/logout";
    private static final String LOGIN_BASE = "/UI/login";

    // Vaadin 8 served this from the theme (ThemeResource); the theme moves in
    // stage 2a. A static resource outside VAADIN/themes: Flow's development
    // mode serves VAADIN/themes/<theme>/ only from the frontend theme folder
    private static final String DEFAULT_PROFILE_IMAGE = "VAADIN/hawkbit/images/profile-pic-57px.jpg";

    private final VaadinMessageSource i18n;

    private final UiProperties uiProperties;

    private final transient HawkbitServerProperties serverProperties;

    private static final long serialVersionUID = 5394474618559481462L;

    // this should be resolved when we introduce event bus on UI to just inform
    // the buttons directly via events
    private final List<ValoMenuItemButton> menuButtons = new ArrayList<>();

    private final transient PermissionService permissionService;

    private final List<DashboardMenuItem> dashboardVaadinViews;

    private String initialViewName;

    private boolean accessibleViewsEmpty;

    private VerticalLayout compositionRoot;

    @Autowired
    DashboardMenu(final VaadinMessageSource i18n, final UiProperties uiProperties,
            final HawkbitServerProperties serverProperties, final PermissionService permissionService,
            final List<DashboardMenuItem> dashboardVaadinViews) {
        this.i18n = i18n;
        this.uiProperties = uiProperties;
        this.serverProperties = serverProperties;
        this.permissionService = permissionService;
        this.dashboardVaadinViews = dashboardVaadinViews;
    }

    /**
     * initializing the view and creating the layout, cannot be done in the
     * constructor because the constructor will be called by spring and the
     * dashboard must be initialized when the dashboard UI is creating.
     */
    public void init() {
        initialViewName = "";
        addClassName("valo-menu");
        setId(ID);
        setSizeUndefined();
        setHeight("100%");
        compositionRoot = buildContent();
        getContent().removeAll();
        getContent().add(compositionRoot);
    }

    private VerticalLayout buildContent() {
        final VerticalLayout dashboardMenuLayout = new VerticalLayout();
        dashboardMenuLayout.setSpacing(false);
        dashboardMenuLayout.setPadding(false);
        dashboardMenuLayout.setSizeFull();

        final VerticalLayout menuContent = getMenuLayout();
        menuContent.add(buildUserMenu(uiProperties));
        menuContent.add(buildToggleButton());

        final VerticalLayout menus = buildMenuItems();
        final VerticalLayout links = buildLinksAndVersion();
        menus.add(links);
        menus.setHorizontalComponentAlignment(Alignment.CENTER, links);
        menus.setFlexGrow(1.0, links);
        menuContent.add(menus);
        menuContent.setFlexGrow(1.0, menus);

        dashboardMenuLayout.add(menuContent);

        return dashboardMenuLayout;
    }

    private static VerticalLayout getMenuLayout() {
        final VerticalLayout menuContent = new VerticalLayout();
        menuContent.setSpacing(false);
        menuContent.setPadding(false);
        menuContent.setWidth(null);
        menuContent.setHeight("100%");

        menuContent.addClassName("valo-menu-part");
        menuContent.addClassName("sidebar");
        menuContent.addClassName("no-vertical-drag-hints");
        menuContent.addClassName("no-horizontal-drag-hints");

        return menuContent;
    }

    private VerticalLayout buildLinksAndVersion() {
        final VerticalLayout links = new VerticalLayout();
        links.setSpacing(true);
        links.setPadding(false);
        links.addClassName("links");
        final String linkStyle = "v-link";

        if (!uiProperties.getLinks().getDocumentation().getRoot().isEmpty()) {
            final Anchor docuLink = SPUIComponentProvider.getLink(UIComponentIdProvider.LINK_DOCUMENTATION,
                    i18n.getMessage("link.documentation.name"), uiProperties.getLinks().getDocumentation().getRoot(),
                    VaadinIcon.QUESTION_CIRCLE, "_blank", linkStyle);
            docuLink.setSizeFull();
            links.add(docuLink);
            links.setHorizontalComponentAlignment(Alignment.CENTER, docuLink);
        }

        if (!uiProperties.getLinks().getUserManagement().isEmpty()) {
            final Anchor userManagementLink = SPUIComponentProvider.getLink(UIComponentIdProvider.LINK_USERMANAGEMENT,
                    i18n.getMessage("link.usermanagement.name"), uiProperties.getLinks().getUserManagement(),
                    VaadinIcon.USERS, "_blank", linkStyle);
            links.add(userManagementLink);
            userManagementLink.setSizeFull();
            links.setHorizontalComponentAlignment(Alignment.CENTER, userManagementLink);
        }

        if (!uiProperties.getLinks().getSupport().isEmpty()) {
            final Anchor supportLink = SPUIComponentProvider.getLink(UIComponentIdProvider.LINK_SUPPORT,
                    i18n.getMessage("link.support.name"), uiProperties.getLinks().getSupport(), VaadinIcon.ENVELOPE_O,
                    "", linkStyle);
            supportLink.setSizeFull();
            links.add(supportLink);
            links.setHorizontalComponentAlignment(Alignment.CENTER, supportLink);

        }

        final Component buildVersionInfo = buildVersionInfo();
        links.add(buildVersionInfo);
        links.setHorizontalComponentAlignment(Alignment.CENTER, buildVersionInfo);
        links.setSizeFull();
        links.setHeight(null);
        return links;
    }

    private static String getImage(final boolean gravatar) {
        if (!gravatar) {
            return DEFAULT_PROFILE_IMAGE;
        }

        return UserDetailsFormatter.getCurrentUserEmail().map(email -> new GravatarResource(email).getURL())
                .orElse(DEFAULT_PROFILE_IMAGE);

    }

    private Component buildUserMenu(final UiProperties uiProperties) {
        final MenuBar settings = new MenuBar();
        settings.addClassName("user-menu");

        final MenuItem settingsItem = settings.addItem(new Image(getImage(uiProperties.isGravatar()), ""));

        // Vaadin 8 rendered this text as HTML (setHtmlContentAllowed); Flow
        // menu items take components, the text is added as plain text
        final String formattedTenant = UserDetailsFormatter.formatCurrentTenant();
        if (StringUtils.hasLength(formattedTenant)) {
            settingsItem.add(new Text(formattedTenant));
            UserDetailsFormatter.getCurrentTenant().ifPresent(tenant -> settingsItem.setTooltipText(i18n
                    .getMessage("menu.user.description", tenant, UserDetailsFormatter.getCurrentUser().getUsername())));
        } else {
            settingsItem.add(new Text("..."));
        }

        settingsItem.setClassName("user-menuitem");

        final String logoutUrl = generateLogoutUrl();

        settingsItem.getSubMenu().addItem(i18n.getMessage("label.sign.out"), selectedItem -> {
            final UI ui = UI.getCurrent();
            ui.getPage().setLocation(logoutUrl);
            ui.getSession().close();
        });
        return settings;
    }

    private static String generateLogoutUrl() {
        final UriComponentsBuilder logout = ServletUriComponentsBuilder.fromCurrentContextPath().path(LOGOUT_BASE);

        UserDetailsFormatter.getCurrentTenant().ifPresent(tenant -> logout.queryParam("login",
                UriComponentsBuilder.fromPath(LOGIN_BASE).queryParam("tenant", tenant).build().toUriString()));

        return logout.toUriString();
    }

    private Component buildToggleButton() {
        final Button valoMenuToggleButton = new Button(i18n.getMessage("label.menu"),
                new MenuToggleClickListenerMyClickListener());
        valoMenuToggleButton.setIcon(VaadinIcon.LIST.create());
        valoMenuToggleButton.addClassName("valo-menu-toggle");
        valoMenuToggleButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
        return valoMenuToggleButton;
    }

    private VerticalLayout buildMenuItems() {
        final VerticalLayout menuItemsLayout = new VerticalLayout();
        menuItemsLayout.setSpacing(false);
        menuItemsLayout.setPadding(false);
        menuItemsLayout.addClassName("valo-menuitems");
        menuItemsLayout.setHeight("100%");

        final List<DashboardMenuItem> accessibleViews = getAccessibleViews();
        if (accessibleViews.isEmpty()) {
            accessibleViewsEmpty = true;
            return menuItemsLayout;
        }
        initialViewName = accessibleViews.get(0).getViewName();
        for (final DashboardMenuItem view : accessibleViews) {
            final ValoMenuItemButton menuItemComponent = new ValoMenuItemButton(view);
            menuButtons.add(menuItemComponent);
            menuItemsLayout.add(buildLabelWrapper(menuItemComponent, view.getNotificationUnreadLabel()));
        }
        return menuItemsLayout;
    }

    /**
     * Creates the wrapper which contains the menu item and the adjacent label for
     * displaying the occurred events
     * 
     * @param menuItemButton
     *            the menu item
     * @param notificationLabel
     *            the label for displaying the occurred events
     * @return Component of type Div
     */
    private static Component buildLabelWrapper(final ValoMenuItemButton menuItemButton,
            final Span notificationLabel) {
        final Div dashboardWrapper = new Div(menuItemButton);
        dashboardWrapper.addClassName("labelwrapper");
        dashboardWrapper.addClassName("valo-menu-item");
        notificationLabel.addClassName("valo-menu-badge");
        notificationLabel.setWidth(null);
        notificationLabel.setVisible(false);
        notificationLabel
                .setId(UIComponentIdProvider.NOTIFICATION_MENU_ID + "." + menuItemButton.getText().toLowerCase());
        dashboardWrapper.add(notificationLabel);
        return dashboardWrapper;
    }

    /**
     * Returns all views which are currently accessible by the current logged in
     * user.
     *
     * @return a list of all views which are currently visible and accessible for
     *         the current logged in user
     */
    private List<DashboardMenuItem> getAccessibleViews() {
        return this.dashboardVaadinViews.stream()
                .filter(view -> permissionService.hasAtLeastOnePermission(view.getPermissions()))
                .collect(Collectors.toList());
    }

    private Component buildVersionInfo() {
        final Span label = new Span();
        label.setSizeFull();
        label.setClassName("version-layout");
        label.setText(serverProperties.getBuild().getVersion());
        return label;
    }

    /**
     * Returns the view name for the start page after login.
     *
     * @return the initialViewName of the start page
     */
    public String getInitialViewName() {
        return initialViewName;
    }

    /**
     * Is a View available.
     *
     * @return the accessibleViewsEmpty <code>true</code> no rights for any view
     *         <code>false</code> a view is available
     */
    public boolean isAccessibleViewsEmpty() {
        return accessibleViewsEmpty;
    }

    /**
     * notifies the dashboard that the view has been changed and the button needs to
     * be re-styled.
     *
     * @param event
     *            the post view change event
     */
    public void postViewChange(final PostViewChangeEvent event) {
        menuButtons.forEach(button -> button.postViewChange(event));
    }

    /**
     * Returns the dashboard view type by a given view name.
     *
     * @param viewName
     *            the name of the view to retrieve
     * @return the dashboard view for a given viewname or {@code null} if view with
     *         given viewname does not exists
     */
    public DashboardMenuItem getByViewName(final String viewName) {

        return dashboardVaadinViews.stream().filter(view -> view.getViewName().equals(viewName)).findAny().orElse(null);
    }

    /**
     * Is the given view accessible.
     *
     * @param viewName
     *            the view name
     * @return <code>true</code> = denied, <code>false</code> = accessible
     */
    public boolean isAccessDenied(final String viewName) {
        return getAccessibleViews().stream()
                .noneMatch(dashboardMenuItem -> dashboardMenuItem.getViewName().equals(viewName));
    }

    private class MenuToggleClickListenerMyClickListener implements ComponentEventListener<ClickEvent<Button>> {

        private static final long serialVersionUID = 1L;
        private static final String STYLE_VISIBLE = "valo-menu-visible";

        @Override
        public void onComponentEvent(final ClickEvent<Button> event) {
            if (compositionRoot.hasClassName(STYLE_VISIBLE)) {
                compositionRoot.removeClassName(STYLE_VISIBLE);
            } else {
                compositionRoot.addClassName(STYLE_VISIBLE);
            }
        }
    }

    /**
     * An menu item button wrapper for the dashboard menu item.
     */
    public static final class ValoMenuItemButton extends Button {

        private static final long serialVersionUID = 1L;

        private static final String STYLE_SELECTED = "selected";

        private final DashboardMenuItem view;

        /**
         * creates a new button in case of pressed switches to the given {@code view}.
         *
         * @param view
         *            the view to switch to in case the button is pressed
         */
        public ValoMenuItemButton(final DashboardMenuItem view) {
            this.view = view;
            setClassName("valo-menu-item");
            setIcon(view.getDashboardIcon().create());
            setText(view.getDashboardCaption());
            setTooltipText(view.getDashboardCaptionLong());
            setId(view.getDashboardCaption().toLowerCase());
            /* Avoid double click */
            setDisableOnClick(true);
            addClickListener(event -> event.getSource().getUI()
                    .ifPresent(ui -> ui.navigate(view.getViewName())));
        }

        /**
         * notifies the button to change his style.
         *
         * @param event
         *            the post view change event
         */
        public void postViewChange(final PostViewChangeEvent event) {
            removeClassName(STYLE_SELECTED);
            if (event.getView() != null && event.getView().equals(view)) {
                addClassName(STYLE_SELECTED);
                /* disable current selected view */
                setEnabled(false);
            } else {
                /* Enable other views */
                setEnabled(true);
            }
        }
    }
}
