/**
 * Copyright (c) 2015 Bosch Software Innovations GmbH and others
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.hawkbit.ui.common.builder;

import org.springframework.util.StringUtils;

import com.vaadin.flow.component.html.Span;

/**
 * Label Builder.
 *
 */
public class LabelBuilder {

    private String caption;

    private String name;

    private String id;

    private boolean visible = true;

    /**
     * Add name to label
     *
     * @param name
     *            the value to set
     * @return builder
     */
    public LabelBuilder name(final String name) {
        this.name = name;
        return this;
    }

    /**
     * Add caption to label
     *
     * @param caption
     *            the caption to set
     * @return builder
     */
    public LabelBuilder caption(final String caption) {
        this.caption = caption;
        return this;
    }

    /**
     * Add id to label
     *
     * @param id
     *            the id to set
     * @return builder
     */
    public LabelBuilder id(final String id) {
        this.id = id;
        return this;
    }

    /**
     * Toggle label visibility
     *
     * @param visible
     *            the visible to set
     * @return builder
     */
    public LabelBuilder visible(final boolean visible) {
        this.visible = visible;
        return this;
    }

    /**
     * Build caption label.
     * 
     * @return Label
     */
    public Span buildCaptionLabel() {
        final Span label = createLabel();
        label.addClassNames("header-caption");
        return label;
    }

    /**
     * Build label.
     * 
     * @return Label
     */
    public Span buildLabel() {
        final Span label = createLabel();
        // Vaadin 8: undefined size (-1px)
        label.setWidth(null);
        label.setHeight(null);
        if (StringUtils.hasText(caption)) {
            // Vaadin 8 caption of the label: Flow spans have none, it is
            // shown as a first child (styled in stage 2a)
            final Span captionText = new Span(caption);
            captionText.addClassName("v-caption");
            label.addComponentAsFirst(captionText);
        }

        return label;
    }

    private Span createLabel() {
        final Span label = new Span(name);
        label.setVisible(visible);
        // Vaadin 8: ValoTheme.LABEL_SMALL and LABEL_BOLD
        label.addClassNames("small", "bold");
        if (id != null) {
            label.setId(id);
        }

        return label;
    }

}
