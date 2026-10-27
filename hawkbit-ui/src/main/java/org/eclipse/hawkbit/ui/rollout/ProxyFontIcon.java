/**
 * Copyright (c) 2015 Bosch Software Innovations GmbH and others
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.hawkbit.ui.rollout;

import java.io.Serializable;

import com.cronutils.utils.StringUtils;
import com.vaadin.flow.component.icon.VaadinIcon;

/**
 * Helper class which holds the details of font icon to be displayed as
 * label/button in grid:
 * <p>
 * <code>RolloutListGrid</code> / <code>RolloutGroupListGrid</code> /
 * <code>RolloutGroupTargetsListGrid</code> / <code>ActionHistoryGrid</code>
 */
public class ProxyFontIcon implements Serializable {
    private static final long serialVersionUID = 1L;

    private final VaadinIcon icon;
    private final String style;
    private final String description;
    private final String color;

    /**
     * Constructor to create icon metadata object.
     *
     * @param icon
     *            the font representing the icon
     */
    public ProxyFontIcon(final VaadinIcon icon) {
        this(icon, "");
    }

    /**
     * Constructor to create icon metadata object.
     *
     * @param icon
     *            the font representing the icon
     * @param style
     *            the style
     */
    public ProxyFontIcon(final VaadinIcon icon, final String style) {
        this(icon, style, "");
    }

    /**
     * Constructor to create icon metadata object.
     *
     * @param icon
     *            the font representing the icon
     * @param style
     *            the style
     * @param description
     *            the description shown as tooltip
     */
    public ProxyFontIcon(final VaadinIcon icon, final String style, final String description) {
        this(icon, style, description, "");
    }

    /**
     * Constructor to create icon metadata object.
     *
     * @param icon
     *            the font representing the icon
     * @param style
     *            the style
     * @param description
     *            the description shown as tooltip
     * @param color
     *            the color of the icon
     */
    public ProxyFontIcon(final VaadinIcon icon, final String style, final String description, final String color) {
        this.icon = icon;
        this.style = style;
        this.description = description;
        this.color = color;
    }

    /**
     * Gets the style.
     *
     * @return the style
     */
    public String getStyle() {
        return style;
    }

    /**
     * Gets the description shown as tooltip.
     *
     * @return the description shown as tooltip.
     */
    public String getDescription() {
        return description;
    }

    /**
     * Gets the HTML of the icon, wrapped in a span with the colour if one is
     * set.
     *
     * @return the HTML of the icon (a Flow {@code vaadin-icon} element)
     */
    public String getHtml() {
        String html = "";
        if (icon != null) {
            // Vaadin 8 FontIcon.getHtml() gave a font glyph span; Flow icons are
            // <vaadin-icon icon="vaadin:x"> elements
            html = icon.create().getElement().getOuterHTML();
        }
        if (!StringUtils.isEmpty(color)) {
            html = "<span style=\"color:" + color + " !important;\">" + html + "</span>";
        }
        return html;
    }
}
