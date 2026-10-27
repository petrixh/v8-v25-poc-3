/**
 * Copyright (c) 2015 Bosch Software Innovations GmbH and others
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.hawkbit.ui.management.targettag.filter;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;

import org.eclipse.hawkbit.repository.model.TargetUpdateStatus;
import org.eclipse.hawkbit.ui.common.data.proxies.ProxyTarget;
import org.eclipse.hawkbit.ui.common.event.EventTopics;
import org.eclipse.hawkbit.ui.common.event.EventView;
import org.eclipse.hawkbit.ui.common.event.FilterChangedEventPayload;
import org.eclipse.hawkbit.ui.common.event.FilterType;
import org.eclipse.hawkbit.ui.components.SPUIComponentProvider;
import org.eclipse.hawkbit.ui.decorators.SPUIButtonStyleSmall;
import org.eclipse.hawkbit.ui.utils.SPUIDefinitions;
import org.eclipse.hawkbit.ui.utils.UIComponentIdProvider;
import org.eclipse.hawkbit.ui.utils.UIMessageIdProvider;
import org.eclipse.hawkbit.ui.utils.VaadinMessageSource;
import org.springframework.util.CollectionUtils;
import org.vaadin.spring.events.EventBus.UIEventBus;

import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;

/**
 * layout of filter by status
 */
public class FilterByStatusLayout extends VerticalLayout {
    private static final long serialVersionUID = 1L;

    private final VaadinMessageSource i18n;
    private final transient UIEventBus eventBus;

    private final TargetTagFilterLayoutUiState targetTagFilterLayoutUiState;

    private static final String BTN_CLICKED_STYLE = "btnClicked";

    private final Button unknown;
    private final Button inSync;
    private final Button pending;
    private final Button error;
    private final Button registered;
    private final Button overdue;

    private final Collection<TargetUpdateStatus> activeStatusFilters;
    private boolean isOverdueFilterActive;

    private final EnumMap<TargetUpdateStatus, Button> statusToButtonMap;

    FilterByStatusLayout(final VaadinMessageSource i18n, final UIEventBus eventBus,
            final TargetTagFilterLayoutUiState targetTagFilterLayoutUiState) {
        this.i18n = i18n;
        this.eventBus = eventBus;
        this.targetTagFilterLayoutUiState = targetTagFilterLayoutUiState;
        this.activeStatusFilters = new HashSet<>();
        this.isOverdueFilterActive = false;

        this.unknown = buildStatusButton(TargetUpdateStatus.UNKNOWN, UIComponentIdProvider.UNKNOWN_STATUS_ICON,
                UIMessageIdProvider.TOOLTIP_TARGET_STATUS_UNKNOWN, "unknownBtn");
        this.inSync = buildStatusButton(TargetUpdateStatus.IN_SYNC, UIComponentIdProvider.INSYNCH_STATUS_ICON,
                UIMessageIdProvider.TOOLTIP_STATUS_INSYNC, "inSynchBtn");
        this.pending = buildStatusButton(TargetUpdateStatus.PENDING, UIComponentIdProvider.PENDING_STATUS_ICON,
                UIMessageIdProvider.TOOLTIP_STATUS_PENDING, "pendingBtn");
        this.error = buildStatusButton(TargetUpdateStatus.ERROR, UIComponentIdProvider.ERROR_STATUS_ICON,
                UIMessageIdProvider.TOOLTIP_STATUS_ERROR, "errorBtn");
        this.registered = buildStatusButton(TargetUpdateStatus.REGISTERED, UIComponentIdProvider.REGISTERED_STATUS_ICON,
                UIMessageIdProvider.TOOLTIP_STATUS_REGISTERED, "registeredBtn");
        this.overdue = buildOverdueButton(UIComponentIdProvider.OVERDUE_STATUS_ICON,
                UIMessageIdProvider.TOOLTIP_STATUS_OVERDUE, "overdueBtn");

        this.statusToButtonMap = new EnumMap<>(TargetUpdateStatus.class);
        this.statusToButtonMap.put(TargetUpdateStatus.UNKNOWN, unknown);
        this.statusToButtonMap.put(TargetUpdateStatus.IN_SYNC, inSync);
        this.statusToButtonMap.put(TargetUpdateStatus.PENDING, pending);
        this.statusToButtonMap.put(TargetUpdateStatus.ERROR, error);
        this.statusToButtonMap.put(TargetUpdateStatus.REGISTERED, registered);

        init();
        buildLayout();
    }

    private Button buildStatusButton(final TargetUpdateStatus status, final String id, final String descriptionMsgKey,
            final String statusStyleName) {
        final Button statusButton = buildFilterButton(id, descriptionMsgKey, statusStyleName);
        statusButton.addClickListener(event -> processClickedStatusButton(status, event.getSource()));

        return statusButton;
    }

    private Button buildFilterButton(final String id, final String descriptionMsgKey, final String statusStyleName) {
        final Button filterButton = SPUIComponentProvider.getButton(id, "", i18n.getMessage(descriptionMsgKey),
                SPUIDefinitions.SP_BUTTON_STATUS_STYLE, false, VaadinIcon.THIN_SQUARE, SPUIButtonStyleSmall.class);
        filterButton.addClassNames(statusStyleName);

        return filterButton;
    }

    private void processClickedStatusButton(final TargetUpdateStatus status, final Button clickedButton) {
        if (activeStatusFilters.contains(status)) {
            clickedButton.removeClassNames(BTN_CLICKED_STYLE);
            activeStatusFilters.remove(status);
        } else {
            clickedButton.addClassNames(BTN_CLICKED_STYLE);
            activeStatusFilters.add(status);
        }

        publishStatusFilterChangedEvent();
    }

    private void publishStatusFilterChangedEvent() {
        eventBus.publish(EventTopics.FILTER_CHANGED, this, new FilterChangedEventPayload<>(ProxyTarget.class,
                FilterType.STATUS, activeStatusFilters, EventView.DEPLOYMENT));

        targetTagFilterLayoutUiState.setClickedTargetUpdateStatusFilters(activeStatusFilters);
    }

    private Button buildOverdueButton(final String id, final String descriptionMsgKey, final String statusStyleName) {
        final Button overdueButton = buildFilterButton(id, descriptionMsgKey, statusStyleName);
        overdueButton.addClickListener(event -> processClickedOverdueButton(event.getSource()));

        return overdueButton;
    }

    private void processClickedOverdueButton(final Button clickedButton) {
        isOverdueFilterActive = !isOverdueFilterActive;

        if (isOverdueFilterActive) {
            clickedButton.addClassNames(BTN_CLICKED_STYLE);
        } else {
            clickedButton.removeClassNames(BTN_CLICKED_STYLE);
        }

        publishOverdueFilterChangedEvent();
    }

    private void publishOverdueFilterChangedEvent() {
        eventBus.publish(EventTopics.FILTER_CHANGED, this, new FilterChangedEventPayload<>(ProxyTarget.class,
                FilterType.OVERDUE, isOverdueFilterActive, EventView.DEPLOYMENT));

        targetTagFilterLayoutUiState.setOverdueFilterClicked(isOverdueFilterActive);
    }

    /**
     * Initialize the Status Layout Component.
     */
    private void init() {
        setPadding(false);
        setSpacing(false);

        addClassNames("target-status-filters");
    }

    private void buildLayout() {
        final Span targetFilterStatusLabel = SPUIComponentProvider.generateLabel(i18n, "label.filter.by.status");
        targetFilterStatusLabel.addClassNames("target-status-filters-title");

        add(targetFilterStatusLabel);
        setHorizontalComponentAlignment(Alignment.START, targetFilterStatusLabel);

        final HorizontalLayout fiterByStatusLayout = buildFilterByStatusLayout();

        add(fiterByStatusLayout);
        setHorizontalComponentAlignment(Alignment.START, fiterByStatusLayout);

        final HorizontalLayout filterByOverdueLayout = buildFilterByOverdueLayout();

        add(filterByOverdueLayout);
        setHorizontalComponentAlignment(Alignment.START, filterByOverdueLayout);
    }

    private HorizontalLayout buildFilterByStatusLayout() {
        final HorizontalLayout buttonLayout = new HorizontalLayout();
        buttonLayout.setPadding(false);
        buttonLayout.setSpacing(false);
        buttonLayout.setClassName("status-button-layout");

        buttonLayout.add(unknown);
        buttonLayout.setVerticalComponentAlignment(Alignment.CENTER, unknown);
        buttonLayout.add(inSync);
        buttonLayout.setVerticalComponentAlignment(Alignment.CENTER, inSync);
        buttonLayout.add(pending);
        buttonLayout.setVerticalComponentAlignment(Alignment.CENTER, pending);
        buttonLayout.add(error);
        buttonLayout.setVerticalComponentAlignment(Alignment.CENTER, error);
        buttonLayout.add(registered);
        buttonLayout.setVerticalComponentAlignment(Alignment.CENTER, registered);

        return buttonLayout;
    }

    private HorizontalLayout buildFilterByOverdueLayout() {
        final HorizontalLayout overdueLayout = new HorizontalLayout();
        overdueLayout.setPadding(false);
        overdueLayout.setSpacing(false);
        overdueLayout.setClassName("overdue-button-layout");

        overdueLayout.add(overdue);
        overdueLayout.setVerticalComponentAlignment(Alignment.CENTER, overdue);

        final Span overdueLabel = SPUIComponentProvider.generateLabel(i18n, "label.filter.by.overdue");

        overdueLayout.add(overdueLabel);
        overdueLayout.setVerticalComponentAlignment(Alignment.CENTER, overdueLabel);

        return overdueLayout;
    }

    /**
     * Remove active status and overdue filters
     */
    public void clearStatusAndOverdueFilters() {
        if (!activeStatusFilters.isEmpty()) {
            removeActiveStatusStyles();
            activeStatusFilters.clear();
            targetTagFilterLayoutUiState.setClickedTargetUpdateStatusFilters(Collections.emptyList());
        }

        if (isOverdueFilterActive) {
            overdue.removeClassNames(BTN_CLICKED_STYLE);
            isOverdueFilterActive = false;
            targetTagFilterLayoutUiState.setOverdueFilterClicked(false);
        }
    }

    private void removeActiveStatusStyles() {
        activeStatusFilters.forEach(status -> statusToButtonMap.get(status).removeClassNames(BTN_CLICKED_STYLE));
    }

    /**
     * Restore the filter state
     */
    public void restoreState() {
        final Collection<TargetUpdateStatus> statusFiltersToRestore = targetTagFilterLayoutUiState
                .getClickedTargetUpdateStatusFilters();

        if (!CollectionUtils.isEmpty(statusFiltersToRestore)) {
            activeStatusFilters.clear();
            activeStatusFilters.addAll(statusFiltersToRestore);
            addActiveStatusStyles();
        }

        if (targetTagFilterLayoutUiState.isOverdueFilterClicked()) {
            isOverdueFilterActive = true;
            overdue.addClassNames(BTN_CLICKED_STYLE);
        }
    }

    private void addActiveStatusStyles() {
        activeStatusFilters.forEach(status -> statusToButtonMap.get(status).addClassNames(BTN_CLICKED_STYLE));
    }
}
