/**
 * Copyright (c) 2020 Bosch.IO GmbH and others
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.hawkbit.ui.management.tag;

import java.awt.Color;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.hawkbit.ui.common.builder.FormComponentBuilder;
import org.eclipse.hawkbit.ui.common.data.proxies.ProxyFilterButton;
import org.eclipse.hawkbit.ui.components.ColorPickerComponent;
import org.eclipse.hawkbit.ui.utils.UIComponentIdProvider;
import org.eclipse.hawkbit.ui.utils.UINotification;
import org.eclipse.hawkbit.ui.utils.VaadinMessageSource;

import com.cronutils.utils.StringUtils;
import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;

/**
 * Builder for tag window layout component
 */
public class TagWindowLayoutComponentBuilder {

    private final VaadinMessageSource i18n;
    private final UINotification uiNotification;

    /**
     * Constructor for TagWindowLayoutComponentBuilder
     *
     * @param i18n
     *            VaadinMessageSource
     * @param uiNotification
     *            UINotification
     */
    public TagWindowLayoutComponentBuilder(final VaadinMessageSource i18n, final UINotification uiNotification) {
        this.i18n = i18n;
        this.uiNotification = uiNotification;
    }

    /**
     * Create name field
     * 
     * @param binder
     *            binder the input will be bound to
     * @return input component
     */
    public TextField createNameField(final Binder<? extends ProxyFilterButton> binder) {
        return FormComponentBuilder.createNameInput(binder, i18n, UIComponentIdProvider.TAG_POPUP_NAME).getComponent();
    }

    /**
     * Create description field
     * 
     * @param binder
     *            binder the input will be bound to
     * @return input component
     */
    public TextArea createDescription(final Binder<? extends ProxyFilterButton> binder) {
        return FormComponentBuilder.createDescriptionInput(binder, i18n, UIComponentIdProvider.TAG_POPUP_DESCRIPTION)
                .getComponent();
    }

    /**
     * Create color picker component
     *
     * @param binder
     *            Filter button binder
     *
     * @return Colour picker component for tags
     */
    public ColorPickerComponent createColorPickerComponent(final Binder<? extends ProxyFilterButton> binder) {
        final ColorPickerComponent colorPickerComponent = new ColorPickerComponent(
                UIComponentIdProvider.TAG_COLOR_PREVIEW_ID, i18n.getMessage("label.choose.tag.color"));

        binder.forField(colorPickerComponent)
                .withConverter(color -> "rgb(" + color.getRed() + "," + color.getGreen() + "," + color.getBlue() + ")",
                        colorString -> {
                            try {
                                return StringUtils.isEmpty(colorString)
                                        ? stringToColor(ProxyFilterButton.DEFAULT_COLOR)
                                        : stringToColor(colorString);
                            } catch (final NumberFormatException e) {
                                try {
                                    return BasicColor.valueOf(colorString.trim().toUpperCase()).getColor();
                                } catch (final IllegalArgumentException ex) {
                                    uiNotification
                                            .displayValidationError(i18n.getMessage("color.not.exists", colorString));
                                    return stringToColor(ProxyFilterButton.DEFAULT_COLOR);
                                }
                            }
                        })
                .bind(ProxyFilterButton::getColour, ProxyFilterButton::setColour);

        return colorPickerComponent;
    }

    // Vaadin 8's ColorUtil.stringToColor (same patterns), for java.awt.Color
    // (A10): the stored colour strings are read as before
    private static final String RGB_NUMBER = "([01]?\\d\\d?|2[0-4]\\d|25[0-5])";
    private static final String SEPARATOR = "(?:\\s*[,+|\\s+]\\s*)";
    private static final String ALPHA = "(0(?:\\.\\d{1,2})?|0?(?:\\.\\d{1,2})|1(?:\\.0{1,2})?)";
    private static final Pattern HEX_PATTERN = Pattern
            .compile("(?i)^#\\s*([\\da-f]{2})([\\da-f]{2})([\\da-f]{2})\\s*$");
    private static final Pattern RGB_PATTERN = Pattern.compile("(?i)^rgb\\(\\s*([01]?\\d{1,2}|2[0-4]\\d|25[0-5])"
            + SEPARATOR + RGB_NUMBER + SEPARATOR + RGB_NUMBER + "\\s*\\)$");
    private static final Pattern RGBA_PATTERN = Pattern.compile("(?i)^rgba\\(\\s*([01]?\\d{1,2}|2[0-4]\\d|25[0-5])"
            + SEPARATOR + RGB_NUMBER + SEPARATOR + RGB_NUMBER + SEPARATOR + ALPHA + "\\s*\\)$");
    private static final String HSL_VALUES = "\\(\\s*([12]?\\d{1,2}|3[0-5]\\d|360)" + SEPARATOR
            + "(\\d{1,2}|100)(?:\\s*%?\\s*[,+|\\s+]\\s*)(\\d{1,2}|100)";
    private static final Pattern HSL_PATTERN = Pattern.compile("(?i)hsl" + HSL_VALUES + "(?:\\s*%?\\s*)\\)$");
    private static final Pattern HSLA_PATTERN = Pattern
            .compile("(?i)hsla" + HSL_VALUES + "(?:\\s*%?[,+|\\s+]\\s*)" + ALPHA + "\\s*\\)$");

    private static Color stringToColor(final String input) {
        Matcher m = HEX_PATTERN.matcher(input);
        if (m.matches()) {
            return new Color(Integer.parseInt(m.group(1), 16), Integer.parseInt(m.group(2), 16),
                    Integer.parseInt(m.group(3), 16));
        }
        m = RGB_PATTERN.matcher(input);
        if (m.matches()) {
            return new Color(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)),
                    Integer.parseInt(m.group(3)));
        }
        m = RGBA_PATTERN.matcher(input);
        if (m.matches()) {
            return new Color(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3)),
                    (int) (Double.parseDouble(m.group(4)) * 255D));
        }
        m = HSL_PATTERN.matcher(input);
        if (m.matches()) {
            return new Color(hslToRgb(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)),
                    Integer.parseInt(m.group(3))));
        }
        m = HSLA_PATTERN.matcher(input);
        if (m.matches()) {
            final Color rgb = new Color(hslToRgb(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)),
                    Integer.parseInt(m.group(3))));
            return new Color(rgb.getRed(), rgb.getGreen(), rgb.getBlue(),
                    (int) (Double.parseDouble(m.group(4)) * 255D));
        }
        throw new NumberFormatException("Parsing color from input failed.");
    }

    // Vaadin 8's Color.HSLtoRGB
    private static int hslToRgb(final int hue, final int saturation, final int lightness) {
        final float h = hue / 360F;
        final float s = saturation / 100F;
        final float l = lightness / 100F;
        if (s == 0F) {
            final int grey = (int) (l * 255F + 0.5F);
            return new Color(grey, grey, grey).getRGB();
        }
        final float q = l < 0.5F ? l * (1F + s) : l + s - l * s;
        final float p = 2F * l - q;
        return new Color(hslComponentToRgb(q, p, h + 1F / 3F), hslComponentToRgb(q, p, h),
                hslComponentToRgb(q, p, h - 1F / 3F)).getRGB();
    }

    private static int hslComponentToRgb(final float q, final float p, final float hue) {
        float h = hue;
        if (h < 0F) {
            h += 1F;
        } else if (h > 1F) {
            h -= 1F;
        }
        final float value;
        if (6F * h < 1F) {
            value = p + (q - p) * 6F * h;
        } else if (2F * h < 1F) {
            value = q;
        } else if (3F * h < 2F) {
            value = p + (q - p) * (2F / 3F - h) * 6F;
        } else {
            value = p;
        }
        return (int) (value * 255F + 0.5F);
    }

    private enum BasicColor {
        BLACK(Color.BLACK), BLUE(Color.BLUE), CYAN(Color.CYAN), GREEN(Color.GREEN), MAGENTA(Color.MAGENTA), RED(
                Color.RED), WHITE(Color.WHITE), YELLOW(Color.YELLOW);

        private final Color color;

        BasicColor(final Color color) {
            this.color = color;
        }

        /**
         * @return Tag color
         */
        public Color getColor() {
            return color;
        }
    }
}
