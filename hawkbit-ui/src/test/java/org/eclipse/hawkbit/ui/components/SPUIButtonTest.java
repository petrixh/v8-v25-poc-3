/**
 * Copyright (c) 2023 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.hawkbit.ui.components;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import io.qameta.allure.Feature;
import io.qameta.allure.Story;

@Feature("Unit Tests - Management UI")
@Story("An icon-only button with a tooltip has the tooltip text as its accessible name")
class SPUIButtonTest {

    @Test
    void iconOnlyButtonWithTooltipGetsAriaLabel() {
        final SPUIButton button = new SPUIButton("id", "", "Save");

        assertThat(button.getAriaLabel()).contains("Save");
    }

    @Test
    void buttonWithCaptionKeepsItsName() {
        final SPUIButton button = new SPUIButton("id", "Caption", "Save");

        assertThat(button.getAriaLabel()).isEmpty();
    }

    @Test
    void iconOnlyButtonWithoutTooltipHasNoAriaLabel() {
        final SPUIButton button = new SPUIButton("id", "", "");

        assertThat(button.getAriaLabel()).isEmpty();
    }
}
