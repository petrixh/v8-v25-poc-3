/**
 * Copyright (c) 2023 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.hawkbit.ui.distributions.dstable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.eclipse.hawkbit.repository.DistributionSetManagement;
import org.eclipse.hawkbit.repository.SystemManagement;
import org.eclipse.hawkbit.repository.model.DistributionSetType;
import org.eclipse.hawkbit.repository.model.TenantMetaData;
import org.eclipse.hawkbit.ui.common.CommonUiDependencies;
import org.eclipse.hawkbit.ui.common.data.proxies.ProxyDistributionSet;
import org.eclipse.hawkbit.ui.common.data.proxies.ProxyTypeInfo;
import org.eclipse.hawkbit.ui.common.event.EventView;
import org.junit.jupiter.api.Test;

import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.data.binder.Binder;

/**
 * The Create Distribution window opens with the tenant's default
 * distribution set type selected: its Type combo box needs a label for it.
 */
class AddDsWindowControllerTest {

    @Test
    void newDistributionSetCarriesTheDefaultTypeWithNameAndKey() {
        final ProxyDistributionSet newDs = newDistributionSetOfTheController();

        final ProxyTypeInfo typeInfo = newDs.getTypeInfo();
        assertThat(typeInfo.getId()).isEqualTo(1L);
        assertThat(typeInfo.getName()).isEqualTo("OS with app(s)");
        assertThat(typeInfo.getKey()).isEqualTo("os_app");
    }

    @Test
    void typeComboBoxOpensWithTheDefaultTypeSelected() {
        final ProxyDistributionSet newDs = newDistributionSetOfTheController();
        final ProxyTypeInfo selectable = new ProxyTypeInfo(1L, "OS with app(s)", "os_app");

        final ComboBox<ProxyTypeInfo> typeCombo = new ComboBox<>();
        typeCombo.setItemLabelGenerator(ProxyTypeInfo::getName);
        typeCombo.setItems(List.of(selectable));
        final Binder<ProxyDistributionSet> binder = new Binder<>();
        binder.forField(typeCombo).bind(ProxyDistributionSet::getTypeInfo, ProxyDistributionSet::setTypeInfo);

        assertThatCode(() -> binder.setBean(newDs)).doesNotThrowAnyException();
        assertThat(typeCombo.getValue()).isEqualTo(selectable);
    }

    private static ProxyDistributionSet newDistributionSetOfTheController() {
        final DistributionSetType defaultType = mock(DistributionSetType.class);
        when(defaultType.getId()).thenReturn(1L);
        when(defaultType.getName()).thenReturn("OS with app(s)");
        when(defaultType.getKey()).thenReturn("os_app");
        final TenantMetaData tenant = mock(TenantMetaData.class);
        when(tenant.getDefaultDsType()).thenReturn(defaultType);
        final SystemManagement systemManagement = mock(SystemManagement.class);
        when(systemManagement.getTenantMetadata()).thenReturn(tenant);

        final AddDsWindowController controller = new AddDsWindowController(mock(CommonUiDependencies.class),
                systemManagement, mock(DistributionSetManagement.class), mock(DsWindowLayout.class),
                mock(EventView.class));
        return controller.buildEntityFromProxy(null);
    }
}
