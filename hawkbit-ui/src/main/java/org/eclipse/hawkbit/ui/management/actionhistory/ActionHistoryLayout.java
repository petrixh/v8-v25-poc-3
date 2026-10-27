/**
 * Copyright (c) 2015 Bosch Software Innovations GmbH and others
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.hawkbit.ui.management.actionhistory;

import org.eclipse.hawkbit.repository.DeploymentManagement;
import org.eclipse.hawkbit.ui.common.CommonUiDependencies;
import org.eclipse.hawkbit.ui.common.EventListenersAwareLayout;

import com.vaadin.flow.component.orderedlayout.HorizontalLayout;

/**
 * Layout responsible for action-history-grid and the corresponding header.
 */
public class ActionHistoryLayout extends HorizontalLayout implements EventListenersAwareLayout {
    private static final long serialVersionUID = 1L;

    private final ActionHistoryGridLayout actionHistoryGridLayout;
    private final ActionStatusGridLayout actionStatusLayout;
    private final ActionStatusMsgGridLayout actionStatusMsgLayout;

    /**
     * Constructor for ActionHistoryLayout
     *
     * @param uiDependencies
     *            {@link CommonUiDependencies}
     * @param deploymentManagement
     *            DeploymentManagement
     * @param actionHistoryGridLayoutUiState
     *            ActionHistoryGridLayoutUiState
     */
    public ActionHistoryLayout(final CommonUiDependencies uiDependencies,
            final DeploymentManagement deploymentManagement,
            final ActionHistoryGridLayoutUiState actionHistoryGridLayoutUiState) {

        this.actionHistoryGridLayout = new ActionHistoryGridLayout(uiDependencies, deploymentManagement,
                actionHistoryGridLayoutUiState);

        this.actionStatusLayout = new ActionStatusGridLayout(uiDependencies, deploymentManagement);
        this.actionStatusMsgLayout = new ActionStatusMsgGridLayout(uiDependencies, deploymentManagement);

        init();
        buildLayout();
    }

    private void init() {
        setSizeFull();
        setPadding(false);
        setSpacing(true);
    }

    private void buildLayout() {
        add(actionHistoryGridLayout);
        setFlexGrow(0.55F, actionHistoryGridLayout);

        actionStatusLayout.setVisible(false);
        add(actionStatusLayout);
        setFlexGrow(0.18F, actionStatusLayout);

        actionStatusMsgLayout.setVisible(false);
        add(actionStatusMsgLayout);
        setFlexGrow(0.27F, actionStatusMsgLayout);
    }

    /**
     * Maximize the action history grid layout
     */
    public void maximize() {
        actionStatusLayout.enableSelection();
        actionStatusMsgLayout.enableSelection();

        actionHistoryGridLayout.maximize();

        actionStatusLayout.setVisible(true);
        actionStatusMsgLayout.setVisible(true);
    }

    /**
     * Minimize the action history grid layout
     */
    public void minimize() {
        actionStatusLayout.disableSelection();
        actionStatusMsgLayout.disableSelection();

        actionHistoryGridLayout.minimize();

        actionStatusLayout.setVisible(false);
        actionStatusMsgLayout.setVisible(false);
    }

    @Override
    public void restoreState() {
        actionHistoryGridLayout.restoreState();
    }

    @Override
    public void onViewEnter() {
        actionHistoryGridLayout.onViewEnter();
    }

    @Override
    public void subscribeListeners() {
        actionHistoryGridLayout.subscribeListeners();
        actionStatusLayout.subscribeListeners();
        actionStatusMsgLayout.subscribeListeners();
    }

    @Override
    public void unsubscribeListeners() {
        actionHistoryGridLayout.unsubscribeListeners();
        actionStatusLayout.unsubscribeListeners();
        actionStatusMsgLayout.unsubscribeListeners();
    }
}
