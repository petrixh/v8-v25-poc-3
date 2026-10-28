/**
 * Copyright (c) 2023 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.hawkbit.ui.common.layout;

import static org.assertj.core.api.Assertions.assertThat;

import com.vaadin.flow.component.html.Div;
import org.junit.jupiter.api.Test;

import io.qameta.allure.Feature;
import io.qameta.allure.Story;

/**
 * A component with an expand ratio shares the free space whatever its content
 * (flex basis 0, no minimum width); a ratio of 0 gives it its own width back.
 */
@Feature("Unit Tests - Management UI")
@Story("Views share their width by the Vaadin 8 expand ratios")
class ExpandRatioLayoutTest {

    private final ExpandRatioLayout layout = new ExpandRatioLayout();
    private final Div grid = new Div();
    private final Div filter = new Div();

    @Test
    void ratioAboveZeroSharesTheFreeSpace() {
        layout.add(filter, grid);

        layout.setFlexGrow(0, filter);
        layout.setFlexGrow(0.45, grid);

        assertThat(layout.getFlexGrow(grid)).isEqualTo(0.45);
        assertThat(grid.getStyle().get("flex-basis")).isEqualTo("0");
        assertThat(grid.getStyle().get("min-width")).isEqualTo("0");
        assertThat(filter.getStyle().get("flex-basis")).isNull();
        assertThat(filter.getStyle().get("min-width")).isNull();
    }

    @Test
    void ratioZeroGivesTheOwnWidthBack() {
        layout.add(grid);
        layout.setFlexGrow(1, grid);

        layout.setFlexGrow(0, grid);

        assertThat(layout.getFlexGrow(grid)).isZero();
        assertThat(grid.getStyle().get("flex-basis")).isNull();
        assertThat(grid.getStyle().get("min-width")).isNull();
    }
}
