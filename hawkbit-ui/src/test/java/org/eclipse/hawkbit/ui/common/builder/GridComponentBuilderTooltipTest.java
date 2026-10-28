/**
 * Copyright (c) 2023 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.hawkbit.ui.common.builder;

import static org.assertj.core.api.Assertions.assertThat;

import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.ComponentUtil;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.Grid.Column;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.shared.Tooltip;

import io.qameta.allure.Feature;
import io.qameta.allure.Story;
import org.junit.jupiter.api.Test;

/**
 * A grid with a tooltip generator on some column covers the tooltips of the
 * components in the cells of the other columns (Flow), so those columns get a
 * generator that reads the component's tooltip text (2B-02).
 */
@Feature("Unit Tests - Management UI")
@Story("Grid component tooltips")
class GridComponentBuilderTooltipTest {

    private static Span icon(final String text) {
        final Span span = new Span();
        Tooltip.forComponent(span).setText(text);
        return span;
    }

    @Test
    void iconColumnReadsTheIconsTooltipWhenAnotherColumnHasAGenerator() {
        final Grid<String> grid = new Grid<>();
        final Column<String> iconColumn = GridComponentBuilder.addIconColumn(grid, item -> icon("Tip " + item), "icon",
                "Icon");
        final Column<String> nestedColumn = GridComponentBuilder.addComponentColumn(grid, item -> {
            final HorizontalLayout layout = new HorizontalLayout();
            layout.add(new Span("no tip"), icon("Nested " + item));
            return layout;
        });
        final Column<String> disabledColumn = GridComponentBuilder.addIconColumn(grid, item -> {
            final Button button = new Button("x");
            button.setTooltipText("Button " + item);
            button.setEnabled(false);
            return button;
        }, "disabled", "Disabled");
        final Column<String> noTipColumn = GridComponentBuilder.addComponentColumn(grid, item -> new Span(item));
        final Column<String> textColumn = grid.addColumn(item -> item);
        textColumn.setTooltipGenerator(item -> "Text " + item);

        ComponentUtil.fireEvent(grid, new AttachEvent(grid, true));

        assertThat(iconColumn.getTooltipGenerator().apply("a")).isEqualTo("Tip a");
        assertThat(nestedColumn.getTooltipGenerator().apply("a")).isEqualTo("Nested a");
        // the cell shows the tooltip of a disabled button, as Vaadin 8 did
        assertThat(disabledColumn.getTooltipGenerator().apply("a")).isEqualTo("Button a");
        assertThat(noTipColumn.getTooltipGenerator().apply("a")).isEmpty();
        assertThat(textColumn.getTooltipGenerator().apply("a")).isEqualTo("Text a");
    }

    @Test
    void gridWithoutAnyGeneratorKeepsTheComponentsOwnTooltips() {
        final Grid<String> grid = new Grid<>();
        final Column<String> iconColumn = GridComponentBuilder.addIconColumn(grid, item -> icon("Tip " + item), "icon",
                "Icon");
        grid.addColumn(item -> item);

        ComponentUtil.fireEvent(grid, new AttachEvent(grid, true));

        assertThat(iconColumn.getTooltipGenerator())
                .isSameAs(new Grid<String>().addColumn(item -> item).getTooltipGenerator());
    }
}
