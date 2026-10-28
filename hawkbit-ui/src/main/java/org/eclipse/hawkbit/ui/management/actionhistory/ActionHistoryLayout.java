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
import org.eclipse.hawkbit.ui.common.layout.ExpandRatioLayout;

/**
 * Layout responsible for action-history-grid and the corresponding header.
 * The three grids share the width by the Vaadin 8 expand ratios
 * ({@link ExpandRatioLayout}).
 */
public class ActionHistoryLayout extends ExpandRatioLayout implements EventListenersAwareLayout {
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

    // Vaadin 8 ratios 0.55 / 0.18 / 0.27, times 100: Vaadin 8 shared the
    // width among the visible components by their ratios, while flex grows
    // that add up to less than 1 leave space free (the history alone took 55 %)
    private void buildLayout() {
        add(actionHistoryGridLayout);
        setFlexGrow(55F, actionHistoryGridLayout);

        actionStatusLayout.setVisible(false);
        add(actionStatusLayout);
        setFlexGrow(18F, actionStatusLayout);

        actionStatusMsgLayout.setVisible(false);
        add(actionStatusMsgLayout);
        setFlexGrow(27F, actionStatusMsgLayout);
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
