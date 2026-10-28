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

import java.util.Arrays;

import com.vaadin.flow.component.HasElement;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;

/**
 * Horizontal layout whose flex grow works as the Vaadin 8 expand ratio did: the
 * components with a ratio share the free space by their ratios, whatever the
 * width of their content. Flow's flex grow alone starts from the content's
 * width, so wide grids and tab sheets made a view wider than the window and the
 * ratios did not hold.
 */
public class ExpandRatioLayout extends HorizontalLayout {

    private static final long serialVersionUID = 1L;

    /**
     * Sets the expand ratio, like Vaadin 8's {@code setExpandRatio}: a ratio
     * above 0 gives the component no width of its own (flex basis 0, no
     * minimum), 0 gives it back its own width.
     */
    @Override
    public void setFlexGrow(final double flexGrow, final HasElement... elementContainers) {
        super.setFlexGrow(flexGrow, elementContainers);
        Arrays.stream(elementContainers).map(HasElement::getElement).forEach(element -> {
            if (flexGrow > 0) {
                element.getStyle().setFlexBasis("0").setMinWidth("0");
            } else {
                element.getStyle().remove("flex-basis").remove("min-width");
            }
        });
    }
}
