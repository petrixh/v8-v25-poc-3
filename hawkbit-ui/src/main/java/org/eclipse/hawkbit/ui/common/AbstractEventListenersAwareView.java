/**
 * Copyright (c) 2021 Bosch.IO GmbH and others
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.hawkbit.ui.common;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

import org.springframework.util.StringUtils;

import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.AfterNavigationEvent;
import com.vaadin.flow.router.AfterNavigationObserver;
import com.vaadin.flow.router.BeforeEvent;
import com.vaadin.flow.router.BeforeLeaveEvent;
import com.vaadin.flow.router.BeforeLeaveObserver;
import com.vaadin.flow.router.HasUrlParameter;
import com.vaadin.flow.router.WildcardParameter;

/**
 * Abstract class managing event listeners aware layouts.
 *
 */
public abstract class AbstractEventListenersAwareView extends VerticalLayout
        implements HasUrlParameter<String>, AfterNavigationObserver, BeforeLeaveObserver, ViewNameAware {
    private static final long serialVersionUID = 1L;

    // directly taken from Vaadin Navigator for consistency
    private static final String DEFAULT_STATE_PARAMETER_SEPARATOR = "&";
    private static final String DEFAULT_STATE_PARAMETER_KEY_VALUE_SEPARATOR = "=";

    private final transient List<EventListenersAwareLayout> eventAwareLayouts = new ArrayList<>();
    private boolean initial;
    private String parameters;

    /**
     * Adds event aware layout.
     * 
     * @param eventAwareLayout
     *            event aware layout to add
     */
    protected void addEventAwareLayout(final EventListenersAwareLayout eventAwareLayout) {
        if (eventAwareLayout != null) {
            eventAwareLayouts.add(eventAwareLayout);
        }
    }

    /**
     * Adds a list of event aware layouts.
     * 
     * @param eventAwareLayouts
     *            event aware layouts to add
     */
    protected void addEventAwareLayouts(final Collection<EventListenersAwareLayout> eventAwareLayouts) {
        eventAwareLayouts.forEach(this::addEventAwareLayout);
    }

    @PostConstruct
    protected void init() {
        buildLayout();
        initial = true;
    }

    /**
     * Builds view layout.
     * 
     */
    protected abstract void buildLayout();

    /**
     * Vaadin 8: the view parameters after the view name ({@code #!view/params}),
     * now the rest of the path ({@code /UI/view/params}).
     */
    @Override
    public void setParameter(final BeforeEvent event, @WildcardParameter final String parameter) {
        this.parameters = parameter;
    }

    /**
     * Vaadin 8: View#enter, called by the Navigator once the view is shown.
     */
    @Override
    public void afterNavigation(final AfterNavigationEvent event) {
        subscribeListeners();

        if (initial) {
            restoreState();
            initial = false;
        } else {
            updateLayoutsOnViewEnter();
        }

        if (StringUtils.hasText(parameters)) {
            handleStateParams(parseStateParameters(parameters));
        }
    }

    private static Map<String, String> parseStateParameters(final String urlParams) {
        return Arrays.stream(urlParams.split(DEFAULT_STATE_PARAMETER_SEPARATOR)).map(paramPair -> {
            final String[] keyValue = paramPair.split(DEFAULT_STATE_PARAMETER_KEY_VALUE_SEPARATOR, 2);
            if (keyValue.length == 2) {
                return new AbstractMap.SimpleEntry<>(keyValue[0], keyValue[1]);
            }
            return null;
        }).filter(Objects::nonNull)
                .collect(Collectors.toMap(AbstractMap.SimpleEntry::getKey, AbstractMap.SimpleEntry::getValue));
    }

    /**
     * Subscribes all listeners of added event aware layouts to event bus.
     * 
     */
    protected void subscribeListeners() {
        eventAwareLayouts.forEach(EventListenersAwareLayout::subscribeListeners);
    }

    /**
     * Restores session state of added event aware layouts.
     * 
     */
    protected void restoreState() {
        eventAwareLayouts.forEach(EventListenersAwareLayout::restoreState);
    }

    /**
     * Handles state url parameters of added event aware layouts.
     * 
     * @param stateParams
     *            map of view state url parameters
     */
    protected void handleStateParams(final Map<String, String> stateParams) {
        eventAwareLayouts.forEach(layout -> layout.handleStateParameters(stateParams));
    }

    /**
     * Called on view enter for added event aware layouts to update their state.
     * 
     */
    protected void updateLayoutsOnViewEnter() {
        eventAwareLayouts.forEach(EventListenersAwareLayout::onViewEnter);
    }

    /**
     * Vaadin 8: View#beforeLeave; Flow continues the navigation unless it is
     * postponed.
     */
    @Override
    public void beforeLeave(final BeforeLeaveEvent event) {
        unsubscribeListeners();
    }

    /**
     * Unsubscribes all listeners of added event aware layouts to event bus.
     * 
     */
    protected void unsubscribeListeners() {
        eventAwareLayouts.forEach(EventListenersAwareLayout::unsubscribeListeners);
    }

    @PreDestroy
    public void destroy() {
        unsubscribeListeners();
    }
}
