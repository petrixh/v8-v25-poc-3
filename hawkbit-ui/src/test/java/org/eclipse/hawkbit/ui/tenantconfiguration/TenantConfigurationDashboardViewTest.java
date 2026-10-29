/**
 * Copyright (c) 2023 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.hawkbit.ui.tenantconfiguration;

import static org.assertj.core.api.Assertions.assertThat;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.shared.Tooltip;

import io.qameta.allure.Feature;
import io.qameta.allure.Story;
import org.junit.jupiter.api.Test;

/**
 * The save and undo buttons of System Config show their tooltip also while
 * disabled, as in Vaadin 8: the tooltip is on a wrapper that stays enabled.
 */
@Feature("Unit Tests - Management UI")
@Story("System Config tooltips")
class TenantConfigurationDashboardViewTest {

    @Test
    void disabledButtonsTooltipIsOnAnEnabledWrapper() {
        final Button button = new Button();
        button.setEnabled(false);

        final Span wrapper = TenantConfigurationDashboardView.withTooltip(button, "Save Configurations");

        assertThat(wrapper.getChildren()).containsExactly(button);
        assertThat(wrapper.isEnabled()).isTrue();
        assertThat(Tooltip.forComponent(wrapper).getText()).isEqualTo("Save Configurations");
        // only one tooltip: the button has none of its own, so an enabled button does not show it twice
        assertThat(button.getTooltip().getText()).isNull();
    }
}
