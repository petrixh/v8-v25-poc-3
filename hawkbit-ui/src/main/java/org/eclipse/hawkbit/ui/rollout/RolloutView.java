/**
 * Copyright (c) 2015 Bosch Software Innovations GmbH and others
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.hawkbit.ui.rollout;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;

import org.eclipse.hawkbit.repository.DistributionSetManagement;
import org.eclipse.hawkbit.repository.EntityFactory;
import org.eclipse.hawkbit.repository.QuotaManagement;
import org.eclipse.hawkbit.repository.RolloutGroupManagement;
import org.eclipse.hawkbit.repository.RolloutManagement;
import org.eclipse.hawkbit.repository.TargetFilterQueryManagement;
import org.eclipse.hawkbit.repository.TargetManagement;
import org.eclipse.hawkbit.repository.TenantConfigurationManagement;
import org.eclipse.hawkbit.security.SystemSecurityContext;
import org.eclipse.hawkbit.tenancy.TenantAware;
import org.eclipse.hawkbit.ui.SpPermissionChecker;
import org.eclipse.hawkbit.ui.UiProperties;
import org.eclipse.hawkbit.ui.common.AbstractEventListenersAwareView;
import org.eclipse.hawkbit.ui.common.CommonUiDependencies;
import org.eclipse.hawkbit.ui.common.event.EventLayout;
import org.eclipse.hawkbit.ui.common.event.EventView;
import org.eclipse.hawkbit.ui.common.event.EventViewAware;
import org.eclipse.hawkbit.ui.common.layout.listener.LayoutVisibilityListener;
import org.eclipse.hawkbit.ui.common.layout.listener.LayoutVisibilityListener.VisibilityHandler;
import org.eclipse.hawkbit.ui.rollout.rollout.RolloutGridLayout;
import org.eclipse.hawkbit.ui.rollout.rolloutgroup.RolloutGroupGridLayout;
import org.eclipse.hawkbit.ui.rollout.rolloutgrouptargets.RolloutGroupTargetGridLayout;
import org.eclipse.hawkbit.ui.utils.UINotification;
import org.eclipse.hawkbit.ui.utils.VaadinMessageSource;
import org.eclipse.hawkbit.utils.TenantConfigHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.vaadin.spring.events.EventBus.UIEventBus;

import com.vaadin.flow.component.orderedlayout.FlexComponent.Alignment;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.spring.annotation.SpringComponent;
import com.vaadin.flow.spring.annotation.UIScope;

import jakarta.annotation.security.PermitAll;

/**
 * Rollout management view.
 */
@UIScope
@SpringComponent
@Route(RolloutView.VIEW_NAME)
@PermitAll
public class RolloutView extends AbstractEventListenersAwareView {
    private static final long serialVersionUID = 1L;

    public static final String VIEW_NAME = "rollout";

    private final RolloutGridLayout rolloutsLayout;
    private final RolloutGroupGridLayout rolloutGroupsLayout;
    private final RolloutGroupTargetGridLayout rolloutGroupTargetsLayout;
    private final RolloutManagementUIState rolloutManagementUIState;

    private final transient LayoutVisibilityListener layoutVisibilityListener;

    @Autowired
    RolloutView(final SpPermissionChecker permissionChecker, final RolloutManagementUIState rolloutManagementUIState,
            final UIEventBus eventBus, final RolloutManagement rolloutManagement,
            final RolloutGroupManagement rolloutGroupManagement, final TargetManagement targetManagement,
            final UINotification uiNotification, final UiProperties uiProperties, final EntityFactory entityFactory,
            final VaadinMessageSource i18n, final TargetFilterQueryManagement targetFilterQueryManagement,
            final QuotaManagement quotaManagement, final TenantConfigurationManagement tenantConfigManagement,
            final DistributionSetManagement distributionSetManagement,
            final SystemSecurityContext systemSecurityContext, final TenantAware tenantAware) {
        this.rolloutManagementUIState = rolloutManagementUIState;

        final CommonUiDependencies uiDependencies = new CommonUiDependencies(i18n, entityFactory, eventBus,
                uiNotification, permissionChecker);

        this.rolloutsLayout = new RolloutGridLayout(uiDependencies, rolloutManagementUIState, rolloutManagement,
                targetManagement, uiProperties, targetFilterQueryManagement, rolloutGroupManagement, quotaManagement,
                tenantConfigManagement, distributionSetManagement, systemSecurityContext);
        this.rolloutGroupsLayout = new RolloutGroupGridLayout(uiDependencies, rolloutGroupManagement,
                rolloutManagementUIState,
                TenantConfigHelper.usingContext(systemSecurityContext, tenantConfigManagement), tenantAware);
        this.rolloutGroupTargetsLayout = new RolloutGroupTargetGridLayout(uiDependencies, rolloutGroupManagement,
                rolloutManagementUIState);

        addEventAwareLayouts(Arrays.asList(rolloutsLayout, rolloutGroupsLayout, rolloutGroupTargetsLayout));

        final Map<EventLayout, VisibilityHandler> layoutVisibilityHandlers = new EnumMap<>(EventLayout.class);
        layoutVisibilityHandlers.put(EventLayout.ROLLOUT_LIST,
                new VisibilityHandler(this::showRolloutListLayout, this::showRolloutGroupListLayout));
        layoutVisibilityHandlers.put(EventLayout.ROLLOUT_GROUP_LIST,
                new VisibilityHandler(this::showRolloutGroupListLayout, this::showRolloutListLayout));
        layoutVisibilityHandlers.put(EventLayout.ROLLOUT_GROUP_TARGET_LIST,
                new VisibilityHandler(this::showRolloutGroupTargetsListLayout, this::showRolloutGroupListLayout));
        this.layoutVisibilityListener = new LayoutVisibilityListener(eventBus, new EventViewAware(EventView.ROLLOUT),
                layoutVisibilityHandlers);
    }

    @Override
    protected void buildLayout() {
        setSpacing(false);
        setPadding(false);
        setSizeFull();

        add(rolloutsLayout);
        setHorizontalComponentAlignment(Alignment.CENTER, rolloutsLayout);
        setFlexGrow(1.0F, rolloutsLayout);

        rolloutGroupsLayout.setVisible(false);
        add(rolloutGroupsLayout);
        setHorizontalComponentAlignment(Alignment.CENTER, rolloutGroupsLayout);
        setFlexGrow(1.0F, rolloutGroupsLayout);

        rolloutGroupTargetsLayout.setVisible(false);
        add(rolloutGroupTargetsLayout);
        setHorizontalComponentAlignment(Alignment.CENTER, rolloutGroupTargetsLayout);
        setFlexGrow(1.0F, rolloutGroupTargetsLayout);
    }

    private void showRolloutListLayout() {
        rolloutManagementUIState.setCurrentLayout(EventLayout.ROLLOUT_LIST);
        rolloutManagementUIState.setSelectedRolloutId(null);
        rolloutManagementUIState.setSelectedRolloutName("");
        rolloutManagementUIState.setSelectedRolloutGroupId(null);
        rolloutManagementUIState.setSelectedRolloutGroupName("");

        rolloutsLayout.setVisible(true);
        rolloutGroupsLayout.setVisible(false);
        rolloutGroupTargetsLayout.setVisible(false);
    }

    private void showRolloutGroupListLayout() {
        rolloutManagementUIState.setCurrentLayout(EventLayout.ROLLOUT_GROUP_LIST);
        rolloutManagementUIState.setSelectedRolloutGroupId(null);
        rolloutManagementUIState.setSelectedRolloutGroupName("");

        rolloutsLayout.setVisible(false);
        rolloutGroupsLayout.setVisible(true);
        rolloutGroupTargetsLayout.setVisible(false);
    }

    private void showRolloutGroupTargetsListLayout() {
        rolloutManagementUIState.setCurrentLayout(EventLayout.ROLLOUT_GROUP_TARGET_LIST);

        rolloutsLayout.setVisible(false);
        rolloutGroupsLayout.setVisible(false);
        rolloutGroupTargetsLayout.setVisible(true);
    }

    @Override
    protected void restoreState() {
        final EventLayout layout = rolloutManagementUIState.getCurrentLayout().orElse(EventLayout.ROLLOUT_LIST);
        switch (layout) {
        case ROLLOUT_LIST:
            showRolloutListLayout();
            break;
        case ROLLOUT_GROUP_LIST:
            showRolloutGroupListLayout();
            break;
        case ROLLOUT_GROUP_TARGET_LIST:
            showRolloutGroupTargetsListLayout();
            break;
        default:
            break;
        }

        super.restoreState();
    }

    @Override
    public String getViewName() {
        return RolloutView.VIEW_NAME;
    }

    @Override
    protected void subscribeListeners() {
        layoutVisibilityListener.subscribe();

        super.subscribeListeners();
    }

    @Override
    protected void unsubscribeListeners() {
        layoutVisibilityListener.unsubscribe();

        super.unsubscribeListeners();
    }
}
