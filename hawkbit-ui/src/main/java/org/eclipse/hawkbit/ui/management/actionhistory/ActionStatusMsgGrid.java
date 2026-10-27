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

import jakarta.annotation.PreDestroy;

import org.eclipse.hawkbit.repository.DeploymentManagement;
import org.eclipse.hawkbit.ui.common.CommonUiDependencies;
import org.eclipse.hawkbit.ui.common.builder.GridComponentBuilder;
import org.eclipse.hawkbit.ui.common.data.providers.ActionStatusMsgDataProvider;
import org.eclipse.hawkbit.ui.common.data.proxies.ProxyActionStatus;
import org.eclipse.hawkbit.ui.common.data.proxies.ProxyMessage;
import org.eclipse.hawkbit.ui.common.event.FilterType;
import org.eclipse.hawkbit.ui.common.grid.AbstractGrid;
import org.eclipse.hawkbit.ui.common.grid.support.FilterSupport;
import org.eclipse.hawkbit.ui.common.grid.support.MasterEntitySupport;
import org.eclipse.hawkbit.ui.common.grid.support.SelectionSupport;
import org.eclipse.hawkbit.ui.utils.SPUIStyleDefinitions;
import org.eclipse.hawkbit.ui.utils.UIComponentIdProvider;
import org.eclipse.hawkbit.ui.utils.UIMessageIdProvider;

import com.vaadin.flow.data.provider.Query;
import com.vaadin.flow.shared.Registration;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.component.textfield.TextArea;

/**
 * This grid presents the messages for a selected action-status.
 */
public class ActionStatusMsgGrid extends AbstractGrid<ProxyMessage, Long> {
    private static final long serialVersionUID = 1L;

    private static final String MSG_ID = "id";
    private static final String VALUE_ID = "msgValue";

    private final transient MasterEntitySupport<ProxyActionStatus> masterEntitySupport;

    private final Registration itemClickListenerRegistration;

    public ActionStatusMsgGrid(final CommonUiDependencies uiDependencies,
            final DeploymentManagement deploymentManagement) {
        super(uiDependencies.getI18n(), uiDependencies.getEventBus(), uiDependencies.getPermChecker());

        setSelectionSupport(new SelectionSupport<>(this));
        getSelectionSupport().enableSingleSelection();

        addClassNames(SPUIStyleDefinitions.ACTION_HISTORY_MESSAGE_GRID);

        setItemDetailsRenderer(new ComponentRenderer<>(ActionStatusMsgGrid::generateDetails));
        // Flow toggles details on click by default; the listener below does it
        setDetailsVisibleOnClick(false);

        this.itemClickListenerRegistration = addItemClickListener(event -> {
            final ProxyMessage msg = event.getItem();
            setDetailsVisible(msg, !isDetailsVisible(msg));
        });

        setFilterSupport(new FilterSupport<>(
                new ActionStatusMsgDataProvider(deploymentManagement, i18n.getMessage("message.no.available")),
                this::hideAllDetails));
        initFilterMappings();

        this.masterEntitySupport = new MasterEntitySupport<>(getFilterSupport());

        init();
    }

    private void hideAllDetails() {
        getDataProvider().fetch(new Query<>()).forEach(msg -> setDetailsVisible(msg, false));
    }

    private void initFilterMappings() {
        getFilterSupport().<Long> addMapping(FilterType.MASTER,
                (filter, masterFilter) -> getFilterSupport().setFilter(masterFilter));
    }

    private static Component generateDetails(final ProxyMessage msg) {
        final TextArea textArea = new TextArea();

        textArea.addClassNames("borderless");
        textArea.addClassNames("tiny");
        textArea.addClassNames("inline-icon");

        textArea.setHeight("120px");
        textArea.setWidth("100%");

        textArea.setValue(msg.getMessage());
        textArea.setReadOnly(Boolean.TRUE);

        return textArea;
    }

    @Override
    public String getGridId() {
        return UIComponentIdProvider.ACTION_HISTORY_MESSAGE_GRID_ID;
    }

    @Override
    public void addColumns() {
        // Vaadin 8: setFrozenColumnCount(2), the two columns frozen
        GridComponentBuilder.addColumn(this, ProxyMessage::getId).setKey(MSG_ID).setHeader("##").setFlexGrow((int) 0)
                .setAutoWidth(true).setFrozen(true);

        GridComponentBuilder.addColumn(this, ProxyMessage::getMessage).setKey(VALUE_ID)
                .setHeader(i18n.getMessage(UIMessageIdProvider.CAPTION_ACTION_MESSAGES)).setFrozen(true);
    }

    /**
     * @return Master entity support
     */
    public MasterEntitySupport<ProxyActionStatus> getMasterEntitySupport() {
        return masterEntitySupport;
    }

    @PreDestroy
    void destroy() {
        itemClickListenerRegistration.remove();
    }
}
