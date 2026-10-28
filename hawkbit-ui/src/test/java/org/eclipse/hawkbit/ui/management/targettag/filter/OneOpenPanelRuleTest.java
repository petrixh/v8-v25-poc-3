/**
 * Copyright (c) 2023 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.hawkbit.ui.management.targettag.filter;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import com.vaadin.flow.component.accordion.Accordion;
import com.vaadin.flow.component.accordion.AccordionPanel;
import com.vaadin.flow.component.html.Div;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.qameta.allure.Feature;
import io.qameta.allure.Story;

/**
 * The filter panel keeps exactly one section open: closing the open section
 * opens it again and does not touch the filter, switching reports the new one.
 */
@Feature("Unit Tests - Management UI")
@Story("Target filter panel keeps one section open")
class OneOpenPanelRuleTest {

    private Accordion accordion;
    private AccordionPanel simple;
    private AccordionPanel custom;
    private AccordionPanel type;
    private final List<AccordionPanel> switches = new ArrayList<>();

    @BeforeEach
    void setUp() {
        accordion = new Accordion();
        simple = accordion.add("Simple", new Div());
        custom = accordion.add("Custom", new Div());
        type = accordion.add("Type", new Div());
        new OneOpenPanelRule(accordion, switches::add);
    }

    @Test
    void firstSectionIsOpenByDefaultAndNothingIsReported() {
        assertThat(accordion.getOpenedPanel()).containsSame(simple);
        assertThat(switches).isEmpty();
    }

    @Test
    void closingTheOpenSectionOpensItAgainWithoutReport() {
        accordion.close();

        assertThat(accordion.getOpenedPanel()).containsSame(simple);
        assertThat(switches).isEmpty();
    }

    @Test
    void closingAfterASwitchReopensTheSwitchedSection() {
        accordion.open(type);
        accordion.close();

        assertThat(accordion.getOpenedPanel()).containsSame(type);
        assertThat(switches).containsExactly(type);
    }

    @Test
    void switchingReportsEachNewSectionOnce() {
        accordion.open(custom);
        accordion.open(type);
        accordion.open(simple);

        assertThat(accordion.getOpenedPanel()).containsSame(simple);
        assertThat(switches).containsExactly(custom, type, simple);
    }

    @Test
    void openingTheOpenSectionAgainReportsNothing() {
        accordion.open(simple);

        assertThat(switches).isEmpty();
    }
}
