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

import java.util.List;
import java.util.stream.Collectors;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.security.PermitAll;

import org.eclipse.hawkbit.ui.UiProperties;
import org.eclipse.hawkbit.ui.common.ViewNameAware;
import org.eclipse.hawkbit.ui.common.data.proxies.ProxySystemConfigWindow;
import org.eclipse.hawkbit.ui.components.SPUIComponentProvider;
import org.eclipse.hawkbit.ui.decorators.SPUIButtonStyleNoBorder;
import org.eclipse.hawkbit.ui.tenantconfiguration.ConfigurationItem.ConfigurationItemChangeListener;
import org.eclipse.hawkbit.ui.utils.UIComponentIdProvider;
import org.eclipse.hawkbit.ui.utils.UINotification;
import org.eclipse.hawkbit.ui.utils.VaadinMessageSource;
import org.springframework.beans.factory.annotation.Autowired;

import com.google.common.collect.Lists;
import com.vaadin.flow.component.Composite;
import com.vaadin.flow.component.HasSize;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.Scroller;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.shared.Tooltip;
import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.router.Route;

/**
 * Main UI for the system configuration view. Vaadin 8 {@code @ViewScope}: a
 * route without a Spring scope, so Flow creates a new instance (through
 * Spring) on each navigation to it.
 */
@Route(TenantConfigurationDashboardView.VIEW_NAME)
@PermitAll
public class TenantConfigurationDashboardView extends Composite<Div>
        implements ViewNameAware, ConfigurationItemChangeListener, HasSize {
    private static final long serialVersionUID = 1L;

    public static final String VIEW_NAME = "spSystemConfig";

    private final VaadinMessageSource i18n;
    private final UiProperties uiProperties;
    private final UINotification uINotification;

    private final List<BaseConfigurationView<? extends ProxySystemConfigWindow>> autowiredConfigurationViews;
    private List<BaseConfigurationView<? extends ProxySystemConfigWindow>> filteredConfigurationViews;

    private Button saveConfigurationBtn;
    private Button undoConfigurationBtn;

    private final List<Binder<? extends ProxySystemConfigWindow>> binders = Lists.newArrayList();

    @Autowired
    TenantConfigurationDashboardView(final VaadinMessageSource i18n, final UiProperties uiProperties,
            final UINotification uINotification,
            final List<BaseConfigurationView<? extends ProxySystemConfigWindow>> configurationViews) {
        this.i18n = i18n;
        this.uiProperties = uiProperties;
        this.uINotification = uINotification;
        this.autowiredConfigurationViews = configurationViews;
    }

    /**
     * Init method adds all Configuration Views to the list of Views.
     */
    @PostConstruct
    public void init() {
        // filter the autowired views
        filteredConfigurationViews = autowiredConfigurationViews.stream().filter(c -> c.getContent().getComponentCount() > 0)
                .filter(ConfigurationGroup::show).collect(Collectors.toList());
        // get and add the binders
        filteredConfigurationViews.forEach(entry -> binders.add(entry.getBinder()));

        // the Vaadin 8 panel scrolled its content (view inventory S12)
        final Scroller rootPanel = new Scroller();
        rootPanel.setClassName("tenantconfig-root");

        final VerticalLayout rootLayout = new VerticalLayout();
        rootLayout.setSizeFull();
        rootLayout.setPadding(true);
        rootLayout.setSpacing(true);
        filteredConfigurationViews.forEach(view -> rootLayout.add(view));

        final HorizontalLayout buttonContent = saveConfigurationButtonsLayout();
        rootLayout.add(buttonContent);
        rootLayout.setAlignSelf(FlexComponent.Alignment.START, buttonContent);
        rootPanel.setContent(rootLayout);
        getContent().add(rootPanel);

        filteredConfigurationViews.forEach(view -> view.addChangeListener(this));
        binders.forEach(binder -> binder.addStatusChangeListener(event -> {
            saveConfigurationBtn.setEnabled(event.getBinder().isValid());
            undoConfigurationBtn.setEnabled(event.getBinder().isValid());
        }));
    }

    private HorizontalLayout saveConfigurationButtonsLayout() {
        final HorizontalLayout hlayout = new HorizontalLayout();
        hlayout.setSpacing(true);
        saveConfigurationBtn = SPUIComponentProvider.getButton(UIComponentIdProvider.SYSTEM_CONFIGURATION_SAVE, "", "",
                "", true, VaadinIcon.HARDDRIVE, SPUIButtonStyleNoBorder.class);
        saveConfigurationBtn.setEnabled(false);
        saveConfigurationBtn.addClickListener(event -> saveConfiguration());
        hlayout.add(withTooltip(saveConfigurationBtn, i18n.getMessage("configuration.savebutton.tooltip")));

        undoConfigurationBtn = SPUIComponentProvider.getButton(UIComponentIdProvider.SYSTEM_CONFIGURATION_CANCEL, "",
                "", "", true, VaadinIcon.ARROW_BACKWARD, SPUIButtonStyleNoBorder.class);
        undoConfigurationBtn.setEnabled(false);
        undoConfigurationBtn.addClickListener(event -> undoConfiguration());
        hlayout.add(withTooltip(undoConfigurationBtn, i18n.getMessage("configuration.cancellbutton.tooltip")));

        final Anchor linkToSystemConfigHelp = SPUIComponentProvider.getHelpLink(i18n,
                uiProperties.getLinks().getDocumentation().getSystemConfigurationView());
        hlayout.add(linkToSystemConfigHelp);

        return hlayout;
    }

    /**
     * Vaadin 8 showed a button's tooltip also while the button was disabled
     * (save and undo are disabled until something changes). A disabled Flow
     * button takes no pointer events, so its tooltip never opens: the tooltip
     * goes on a wrapper around the button, which gets the pointer in both
     * states. Moving the tooltip off the button took the description the
     * button had, so the tooltip text is also set as the button's accessible
     * name (these two icon-only buttons only; other icon-only buttons keep only
     * their tooltip, as in the old app).
     */
    static Span withTooltip(final Button button, final String tooltipText) {
        button.setAriaLabel(tooltipText);
        final Span wrapper = new Span(button);
        Tooltip.forComponent(wrapper).setText(tooltipText);
        return wrapper;
    }

    private void saveConfiguration() {
        final boolean isUserInputValid = filteredConfigurationViews.stream()
                .allMatch(ConfigurationGroup::isUserInputValid);

        if (!isUserInputValid) {
            uINotification.displayValidationError(i18n.getMessage("notification.configuration.save.notpossible"));
            return;
        }
        // Iterate through all View Beans and call their save implementation
        filteredConfigurationViews.forEach(ConfigurationGroup::save);

        // More methods
        saveConfigurationBtn.setEnabled(false);
        undoConfigurationBtn.setEnabled(false);
        uINotification.displaySuccess(i18n.getMessage("notification.configuration.save.successful"));
    }

    private void undoConfiguration() {
        filteredConfigurationViews.forEach(ConfigurationGroup::undo);
        // More methods
        saveConfigurationBtn.setEnabled(false);
        undoConfigurationBtn.setEnabled(false);
    }

    @Override
    public void configurationHasChanged() {
        saveConfigurationBtn.setEnabled(true);
        undoConfigurationBtn.setEnabled(true);
    }

    @Override
    public String getViewName() {
        return TenantConfigurationDashboardView.VIEW_NAME;
    }
}
