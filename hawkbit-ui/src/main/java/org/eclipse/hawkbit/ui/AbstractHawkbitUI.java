/**
 * Copyright (c) 2015 Bosch Software Innovations GmbH and others
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.hawkbit.ui;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

import org.eclipse.hawkbit.ui.components.NotificationUnreadButton;
import org.eclipse.hawkbit.ui.menu.DashboardEvent.PostViewChangeEvent;
import org.eclipse.hawkbit.ui.menu.DashboardMenu;
import org.eclipse.hawkbit.ui.menu.DashboardMenuItem;
import org.eclipse.hawkbit.ui.push.EventPushStrategy;
import org.eclipse.hawkbit.ui.push.UIEventProvider;
import org.eclipse.hawkbit.ui.utils.HawkbitCommonUtil;
import org.eclipse.hawkbit.ui.utils.SPDateTimeUtil;
import org.eclipse.hawkbit.ui.utils.VaadinMessageSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationListener;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.util.ObjectUtils;
import org.vaadin.spring.events.EventBus.UIEventBus;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.DetachEvent;
import com.vaadin.flow.component.HasElement;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.FlexComponent.Alignment;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.AfterNavigationEvent;
import com.vaadin.flow.router.AfterNavigationObserver;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.HasErrorParameter;
import com.vaadin.flow.router.InvalidLocationException;
import com.vaadin.flow.router.Location;
import com.vaadin.flow.router.NotFoundException;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouterLayout;
import com.vaadin.flow.server.ErrorHandler;
import com.vaadin.flow.server.VaadinSession;

import jakarta.annotation.security.PermitAll;

/**
 * Vaadin management UI.
 *
 * Vaadin 25: the shell around every route (menu, view title, notifications
 * button, content). The runtime's subclass is annotated {@code @Layout}, so
 * every {@code @Route} is shown inside it. The Vaadin 8 Navigator and its
 * view provider are replaced by the Flow router: the empty route
 * ({@link EmptyView}) forwards to the first accessible view or to an old
 * {@code #!view} address, and a view the user may not open goes to the error
 * view, as before.
 *
 * {@code @PermitAll}: Flow's navigation access control also checks the
 * {@code @Layout} of a route (found here through the subclass), so the shell
 * must allow any logged-in user, as the Vaadin 8 UI did; the per-view menu
 * permissions are checked in {@link #beforeEnter(BeforeEnterEvent)}.
 */
@PermitAll
public abstract class AbstractHawkbitUI extends HorizontalLayout
        implements RouterLayout, BeforeEnterObserver, AfterNavigationObserver {
    private static final long serialVersionUID = 1L;

    private static final Logger LOG = LoggerFactory.getLogger(AbstractHawkbitUI.class);

    private static final String TITLE = "hawkBit Update Server";

    private static final String EMPTY_VIEW = "";

    private final VaadinMessageSource i18n;
    private final UiProperties uiProperties;

    private Span viewTitle;
    private Div content;

    private final DashboardMenu dashboardMenu;
    private final NotificationUnreadButton notificationUnreadButton;

    private final transient ApplicationContext context;
    private final transient EventPushStrategy pushStrategy;
    private final transient ErrorHandler uiErrorHandler;

    private final transient HawkbitEntityEventListener entityEventsListener;

    protected AbstractHawkbitUI(final EventPushStrategy pushStrategy, final UIEventBus eventBus,
            final UIEventProvider eventProvider, final ApplicationContext context, final DashboardMenu dashboardMenu,
            final NotificationUnreadButton notificationUnreadButton, final UiProperties uiProperties,
            final VaadinMessageSource i18n, final ErrorHandler uiErrorHandler) {
        this.pushStrategy = pushStrategy;
        this.context = context;
        this.dashboardMenu = dashboardMenu;
        this.notificationUnreadButton = notificationUnreadButton;
        this.uiProperties = uiProperties;
        this.i18n = i18n;
        this.uiErrorHandler = uiErrorHandler;

        this.entityEventsListener = new HawkbitEntityEventListener(eventBus, eventProvider, notificationUnreadButton);

        init();
    }

    @Override
    protected void onDetach(final DetachEvent event) {
        LOG.debug("ManagementUI is detached uiid - {}", event.getUI().getUIId());

        entityEventsListener.unsubscribeListeners();

        if (pushStrategy != null) {
            pushStrategy.clean();
            clearContextListener();
        }
    }

    private void clearContextListener() {
        if (pushStrategy instanceof ApplicationListener && context instanceof AbstractApplicationContext) {
            final ApplicationListener<?> listener = (ApplicationListener<?>) pushStrategy;
            ((AbstractApplicationContext) context).getApplicationListeners().remove(listener);

            // we do not need to explicitly remove the listener from
            // ApplicationEventMulticaster because it is done by
            // UIBeanStore#destroy delegating to
            // ApplicationListenerDetector#postProcessBeforeDestruction
        }
    }

    private void init() {
        final UI ui = UI.getCurrent();
        LOG.debug("ManagementUI init starts uiid - {}", ui.getUIId());
        if (pushStrategy != null) {
            pushStrategy.init(ui);
        }

        addClassName("valo-menu-responsive");

        setPadding(false);
        setSpacing(false);
        setSizeFull();

        HawkbitCommonUtil.initLocalization(ui, uiProperties.getLocalization(), i18n);
        SPDateTimeUtil.initializeFixedTimeZoneProperty(uiProperties.getFixedTimeZone());

        dashboardMenu.init();

        final VerticalLayout contentVerticalLayout = new VerticalLayout();
        contentVerticalLayout.setPadding(false);
        contentVerticalLayout.setSpacing(false);
        contentVerticalLayout.setSizeFull();
        contentVerticalLayout.setClassName("main-content");
        contentVerticalLayout.add(buildHeader());
        contentVerticalLayout.add(buildViewTitle());

        content = buildContent();
        contentVerticalLayout.add(content);
        contentVerticalLayout.setFlexGrow(1.0, content);

        final String footerNotification = uiProperties.getNotification().getText();
        if (!ObjectUtils.isEmpty(footerNotification)) {
            contentVerticalLayout.add(buildFooterNotification(footerNotification));
        }

        add(dashboardMenu);
        add(contentVerticalLayout);
        setFlexGrow(1.0, contentVerticalLayout);

        // Vaadin 8 set the handler on the UI; Flow has it on the session
        VaadinSession.getCurrent().setErrorHandler(uiErrorHandler);

        ui.getPage().setTitle(TITLE);

        LOG.debug("Current locale of the application is : {}", ui.getLocale());
    }

    @Override
    public void showRouterLayoutContent(final HasElement routerContent) {
        content.getElement().removeAllChildren();
        if (routerContent != null) {
            content.getElement().appendChild(routerContent.getElement());
        }
    }

    @Override
    public void removeRouterLayoutContent(final HasElement oldContent) {
        content.getElement().removeAllChildren();
    }

    /**
     * Vaadin 8: ManagementViewProvider#getStartView. A view that exists but
     * that the user may not open goes to the error view.
     */
    @Override
    public void beforeEnter(final BeforeEnterEvent event) {
        if (HasErrorParameter.class.isAssignableFrom(event.getNavigationTarget())) {
            return;
        }
        final String viewName = event.getLocation().getFirstSegment();
        if (dashboardMenu.getByViewName(viewName) != null && dashboardMenu.isAccessDenied(viewName)) {
            event.rerouteToError(NotFoundException.class);
        }
    }

    /**
     * Vaadin 8: the Navigator's afterViewChange listener. Its view name was the
     * whole navigation state when the error view was shown, so a denied view
     * with parameters has no title.
     */
    @Override
    public void afterNavigation(final AfterNavigationEvent event) {
        final boolean errorView = !event.getActiveChain().isEmpty()
                && event.getActiveChain().get(0) instanceof HasErrorParameter;
        final String viewName = errorView ? event.getLocation().getPath() : event.getLocation().getFirstSegment();
        final DashboardMenuItem view = dashboardMenu.getByViewName(viewName);
        dashboardMenu.postViewChange(new PostViewChangeEvent(view));
        UI.getCurrent().getPage().setTitle(TITLE);
        if (view == null) {
            viewTitle.setText("");
            return;
        }
        viewTitle.setText(view.getDashboardCaptionLong());
    }

    private static Component buildFooterNotification(final String text) {
        final Span notification = new Span();
        notification.getElement().setProperty("innerHTML", text);
        notification.setWidth("100%");
        return notification;
    }

    private static Div buildContent() {
        final Div content = new Div();
        content.setSizeFull();
        content.setClassName("view-content");
        return content;
    }

    private HorizontalLayout buildViewTitle() {
        final HorizontalLayout viewHeadercontent = new HorizontalLayout();
        viewHeadercontent.setPadding(false);
        viewHeadercontent.setSpacing(false);
        viewHeadercontent.setWidth("100%");
        viewHeadercontent.addClassName("view-header-layout");

        viewTitle = new Span();
        viewTitle.setWidth("100%");
        viewTitle.setClassName("header-content");
        viewHeadercontent.add(viewTitle);

        viewHeadercontent.add(notificationUnreadButton);
        viewHeadercontent.setVerticalComponentAlignment(Alignment.CENTER, notificationUnreadButton);
        return viewHeadercontent;
    }

    private static Component buildHeader() {
        final Div cssLayout = new Div();
        cssLayout.setClassName("view-header");
        return cssLayout;
    }

    /**
     * The empty route ({@code /UI/}), Vaadin 8's {@code Navigator.EmptyView}
     * with ManagementViewProvider#getStartView: forwards an old
     * {@code /UI/#!view/parameters} address to {@code /UI/view/parameters},
     * otherwise opens the first view the user may access. With no accessible
     * view it stays empty, as before.
     */
    @Route(EMPTY_VIEW)
    @PermitAll
    public static class EmptyView extends Div implements BeforeEnterObserver {
        private static final long serialVersionUID = 1L;

        private static final String OLD_FRAGMENT_PREFIX = "!";

        /**
         * An old address that is no path inside the UI: a scheme, an empty,
         * {@code .} or {@code ..} segment, a backslash, a semicolon or an encoded
         * {@code / \ . % ;} (the server's firewall rejects such a path with 400).
         */
        private static final Pattern NOT_A_UI_PATH = Pattern
                .compile("(?i)^[^/]*:|//|\\\\|;|%2f|%5c|%2e|%25|%3b|(^|/)\\.{1,2}(/|$)");

        private final DashboardMenu dashboardMenu;
        private final VaadinMessageSource i18n;

        @Autowired
        EmptyView(final DashboardMenu dashboardMenu, final VaadinMessageSource i18n) {
            this.dashboardMenu = dashboardMenu;
            this.i18n = i18n;
        }

        @Override
        public void beforeEnter(final BeforeEnterEvent event) {
            final UI ui = event.getUI();
            // the fragment is never sent to the server: ask the browser
            ui.getPage().fetchCurrentURL(url -> {
                final String fragment = url.getRef();
                final String oldState = fragment != null && fragment.startsWith(OLD_FRAGMENT_PREFIX)
                        ? fragment.substring(OLD_FRAGMENT_PREFIX.length())
                        : null;
                // Vaadin 8: the view name is the old address up to the first
                // "/", read decoded; an empty one (#!//host) opens the start view
                if (oldState != null && !decode(oldState).startsWith("/")) {
                    forwardOldAddress(ui, oldState);
                } else if (!dashboardMenu.isAccessibleViewsEmpty()) {
                    ui.navigate(dashboardMenu.getInitialViewName());
                }
            });
        }

        private static String decode(final String oldState) {
            try {
                return URLDecoder.decode(oldState.replace("+", "%2B"), StandardCharsets.UTF_8);
            } catch (final IllegalArgumentException e) {
                return oldState;
            }
        }

        /**
         * An old address that is no path inside the UI (for example
         * {@code #!http://x} or {@code #!../x}) shows the error view's text here,
         * in the shell, as Vaadin 8 showed it for an unknown view name: such an
         * address cannot be forwarded as a path.
         */
        private void forwardOldAddress(final UI ui, final String oldState) {
            boolean path = !NOT_A_UI_PATH.matcher(oldState).find();
            try {
                new Location(oldState);
            } catch (final InvalidLocationException e) {
                LOG.debug("Old address {} is not a valid path, showing the error view text", oldState, e);
                path = false;
            }
            if (!path) {
                add(new Span(i18n.getMessage("message.error.view", decode(oldState))));
                return;
            }
            // A page load of the new address, not ui.navigate: the page is loaded with the server-side navigation
            // (eagerServerLoad, E006), and a client navigation sent in its first response is overridden by the
            // client's own initial navigation to /UI/. The page load also answers 404 for an unknown view.
            // An address that would leave the UI (another origin, a scheme, a path outside /UI/) is made one encoded segment.
            ui.getPage().executeJs("""
                    const base = new URL(document.baseURI);
                    let target = new URL($0, base);
                    if (target.origin !== base.origin || !target.pathname.startsWith(base.pathname)) {
                        target = new URL(encodeURIComponent($0), base);
                    }
                    window.location.replace(target);""", oldState);
        }
    }
}
