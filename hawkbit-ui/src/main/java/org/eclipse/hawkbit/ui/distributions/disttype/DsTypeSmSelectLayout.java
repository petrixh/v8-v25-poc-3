/**
 * Copyright (c) 2015 Bosch Software Innovations GmbH and others
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.hawkbit.ui.distributions.disttype;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.hawkbit.repository.SoftwareModuleTypeManagement;
import org.eclipse.hawkbit.repository.model.SoftwareModuleType;
import org.eclipse.hawkbit.ui.common.data.mappers.TypeToProxyTypeMapper;
import org.eclipse.hawkbit.ui.common.data.proxies.ProxyIdentifiableEntity;
import org.eclipse.hawkbit.ui.common.data.proxies.ProxyType;
import org.eclipse.hawkbit.ui.components.SPUIComponentProvider;
import org.eclipse.hawkbit.ui.decorators.SPUIButtonStyleNoBorder;
import org.eclipse.hawkbit.ui.utils.UIComponentIdProvider;
import org.eclipse.hawkbit.ui.utils.VaadinMessageSource;
import org.springframework.data.domain.PageRequest;
import org.springframework.util.CollectionUtils;

import com.google.common.collect.Sets;
import com.vaadin.flow.component.AbstractField.ComponentValueChangeEvent;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.customfield.CustomField;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent.Alignment;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;

/**
 * Layout for the software modules select grids for managing Distribution Set
 * Types on the Distributions View.
 */
public class DsTypeSmSelectLayout extends CustomField<Set<ProxyType>> {
    private static final long serialVersionUID = 1L;

    private static final int MAX_SM_TYPE_QUERY = 500;

    private final VaadinMessageSource i18n;

    private final transient TypeToProxyTypeMapper<SoftwareModuleType> smTypeToProxyTypeMapper;

    private SmTypeSelectedGrid selectedGrid;
    private SmTypeSourceGrid sourceGrid;

    private final List<ProxyType> allSmTypes;
    private Set<ProxyType> selectedSmTypes;

    private final HorizontalLayout layout;

    /**
     * Constructor
     * 
     * @param i18n
     *            VaadinMessageSource
     * @param softwareModuleTypeManagement
     *            SoftwareModuleTypeManagement
     */
    public DsTypeSmSelectLayout(final VaadinMessageSource i18n,
            final SoftwareModuleTypeManagement softwareModuleTypeManagement) {
        this.i18n = i18n;
        this.smTypeToProxyTypeMapper = new TypeToProxyTypeMapper<>();

        this.allSmTypes = softwareModuleTypeManagement.findAll(PageRequest.of(0, MAX_SM_TYPE_QUERY))
                .map(smTypeToProxyTypeMapper::map).getContent();
        this.selectedSmTypes = new HashSet<>();

        this.layout = new HorizontalLayout();
        this.layout.setSpacing(false);
        this.layout.setPadding(false);
        this.layout.setSizeFull();
        this.layout.setWidth("400px");

        buildLayout();
        add(layout);
    }

    private void buildLayout() {
        final VerticalLayout selectButtonLayout = new VerticalLayout();
        selectButtonLayout.setSpacing(false);
        selectButtonLayout.setPadding(false);

        final Button selectButton = SPUIComponentProvider.getButton(UIComponentIdProvider.SELECT_DIST_TYPE, "", "",
                "arrow-button", true, VaadinIcon.FORWARD, SPUIButtonStyleNoBorder.class);
        selectButton.addClickListener(event -> addSmTypeToSelectedGrid());

        final Button unSelectButton = SPUIComponentProvider.getButton("unselect-dist-type", "", "", "arrow-button",
                true, VaadinIcon.BACKWARDS, SPUIButtonStyleNoBorder.class);
        unSelectButton.addClickListener(event -> removeSmTypeFromSelectedGrid());

        selectButtonLayout.add(selectButton);
        selectButtonLayout.add(unSelectButton);
        selectButtonLayout.setHorizontalComponentAlignment(Alignment.CENTER, selectButton, unSelectButton);

        sourceGrid = buildSourceGrid();
        selectedGrid = buildSelectedGrid();

        layout.add(sourceGrid);
        layout.add(selectButtonLayout);
        layout.add(selectedGrid);
        layout.setVerticalComponentAlignment(Alignment.CENTER, sourceGrid, selectButtonLayout, selectedGrid);
        layout.setFlexGrow(0.45F, sourceGrid);
        layout.setFlexGrow(0.07F, selectButtonLayout);
        layout.setFlexGrow(0.48F, selectedGrid);
    }

    private void addSmTypeToSelectedGrid() {
        final Set<ProxyType> selectedSourceSmTypes = sourceGrid.getSelectedItems();
        if (CollectionUtils.isEmpty(selectedSourceSmTypes)) {
            return;
        }

        setValue(Sets.union(selectedSmTypes, selectedSourceSmTypes).immutableCopy());
    }

    private void removeSmTypeFromSelectedGrid() {
        final Set<ProxyType> selectedSelectedSmTypes = selectedGrid.getSelectedItems();
        if (CollectionUtils.isEmpty(selectedSelectedSmTypes)) {
            return;
        }

        setValue(Sets.difference(selectedSmTypes, selectedSelectedSmTypes).immutableCopy());
    }

    private SmTypeSourceGrid buildSourceGrid() {
        final SmTypeSourceGrid grid = new SmTypeSourceGrid(i18n);
        grid.setItems(allSmTypes);

        if (!CollectionUtils.isEmpty(allSmTypes)) {
            grid.select(allSmTypes.get(0));
        }

        return grid;
    }

    private SmTypeSelectedGrid buildSelectedGrid() {
        // we fire value change event to validate binder in case of mandatory
        // property change because binder itself does not track modified
        // software module types within collection
        final SmTypeSelectedGrid smTypeSelectedGrid = new SmTypeSelectedGrid(i18n,
                () -> fireEvent(new ComponentValueChangeEvent<>(this, this, selectedSmTypes, false)));
        smTypeSelectedGrid.setItems(selectedSmTypes);

        return smTypeSelectedGrid;
    }

    @Override
    public Set<ProxyType> getValue() {
        return selectedSmTypes;
    }

    @Override
    protected Set<ProxyType> generateModelValue() {
        return selectedSmTypes;
    }

    @Override
    protected void setPresentationValue(final Set<ProxyType> value) {
        if (value == null) {
            return;
        }

        selectedSmTypes = value;

        selectedGrid.setItems(selectedSmTypes);
        sourceGrid.setItems(getSourceSmTypes());
    }

    private List<ProxyType> getSourceSmTypes() {
        final Set<Long> selectedSmTypeIds = selectedSmTypes.stream().map(ProxyIdentifiableEntity::getId)
                .collect(Collectors.toSet());
        return allSmTypes.stream().filter(smType -> !selectedSmTypeIds.contains(smType.getId()))
                .collect(Collectors.toList());
    }
}
