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
 */
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
     * Vaadin 8: the Navigator's afterViewChange listener.
     */
    @Override
    public void afterNavigation(final AfterNavigationEvent event) {
        final DashboardMenuItem view = dashboardMenu.getByViewName(event.getLocation().getFirstSegment());
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

        private final DashboardMenu dashboardMenu;

        @Autowired
        EmptyView(final DashboardMenu dashboardMenu) {
            this.dashboardMenu = dashboardMenu;
        }

        @Override
        public void beforeEnter(final BeforeEnterEvent event) {
            final UI ui = event.getUI();
            // the fragment is never sent to the server: ask the browser
            ui.getPage().fetchCurrentURL(url -> {
                final String fragment = url.getRef();
                if (fragment != null && fragment.startsWith(OLD_FRAGMENT_PREFIX)) {
                    ui.navigate(fragment.substring(OLD_FRAGMENT_PREFIX.length()));
                } else if (!dashboardMenu.isAccessibleViewsEmpty()) {
                    ui.navigate(dashboardMenu.getInitialViewName());
                }
            });
        }
    }
}
