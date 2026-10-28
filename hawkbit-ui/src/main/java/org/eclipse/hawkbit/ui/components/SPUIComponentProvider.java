/**
 * Copyright (c) 2015 Bosch Software Innovations GmbH and others
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.hawkbit.ui.components;

import org.eclipse.hawkbit.ui.common.builder.LabelBuilder;
import org.eclipse.hawkbit.ui.decorators.SPUIButtonDecorator;
import org.eclipse.hawkbit.ui.rollout.ProxyFontIcon;
import org.eclipse.hawkbit.ui.utils.VaadinMessageSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import com.vaadin.flow.data.provider.DataProvider;
import com.vaadin.flow.data.provider.ListDataProvider;
import com.vaadin.flow.component.ItemLabelGenerator;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.combobox.ComboBoxVariant;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.shared.Tooltip;

/**
 * Util class to get the Vaadin UI components for the SP-OS main UI. Factory
 * Approach to create necessary UI component which are decorated Aspect of fine
 * tuning the component or extending the component is separated.
 *
 */
public final class SPUIComponentProvider {
    private static final Logger LOG = LoggerFactory.getLogger(SPUIComponentProvider.class);

    /**
     * Prevent Instance creation as utility class.
     */
    private SPUIComponentProvider() {

    }

    /**
     * Get Button - Factory Approach for decoration.
     *
     * @param id
     *            as string
     * @param buttonName
     *            as string
     * @param buttonDesc
     *            as string
     * @param style
     *            string as string
     * @param setStyle
     *            string as boolean
     * @param icon
     *            as image
     * @param buttonDecoratorclassName
     *            as decorator
     * @return Button as UI
     */
    public static Button getButton(final String id, final String buttonName, final String buttonDesc,
            final String style, final boolean setStyle, final VaadinIcon icon,
            final Class<? extends SPUIButtonDecorator> buttonDecoratorclassName) {
        Button button = null;
        SPUIButtonDecorator buttonDecorator = null;
        try {
            // Create instance
            buttonDecorator = buttonDecoratorclassName.newInstance();
            // Decorate button
            button = buttonDecorator.decorate(new SPUIButton(id, buttonName, buttonDesc), style, setStyle, icon);
        } catch (final InstantiationException exception) {
            LOG.error("Error occured while creating Button decorator-" + buttonName, exception);
        } catch (final IllegalAccessException exception) {
            LOG.error("Error occured while acessing Button decorator-" + buttonName, exception);
        }
        return button;
    }

    /**
     * Method to create a link.
     *
     * @param id
     *            of the link
     * @param name
     *            of the link
     * @param resource
     *            path of the link
     * @param icon
     *            of the link
     * @param targetOpen
     *            specify how the link should be open (f. e. new windows =
     *            _blank)
     * @param style
     *            chosen style of the link. Might be {@code null} if no style
     *            should be used
     * @return a link UI component
     */
    public static Anchor getLink(final String id, final String name, final String resource, final VaadinIcon icon,
            final String targetOpen, final String style) {

        final Anchor link = new Anchor(resource, name);
        link.setId(id);
        if (icon != null) {
            link.addComponentAsFirst(icon.create());
        }
        Tooltip.forComponent(link).setText(name);

        link.setTarget(targetOpen);
        if (style != null) {
            link.setClassName(style);
        }

        return link;

    }

    /**
     * Generates help/documentation links from within management UI.
     *
     * @param i18n
     *            the i18n
     * @param uri
     *            to documentation site
     *
     * @return generated link
     */
    public static Anchor getHelpLink(final VaadinMessageSource i18n, final String uri) {

        final Anchor link = new Anchor(uri, "");
        link.setTarget("_blank");
        link.addComponentAsFirst(VaadinIcon.QUESTION_CIRCLE.create());
        Tooltip.forComponent(link).setText(i18n.getMessage("tooltip.documentation.link"));
        return link;

    }

    /**
     * Generate a label containing an icon
     * 
     * @param fontIcon
     *            icon to display in the label
     * @param id
     *            label ID
     * @return the label
     */
    public static Span getLabelIcon(final ProxyFontIcon fontIcon, final String id) {
        if (fontIcon == null) {
            return new Span("");
        }

        // Label with ContentMode.HTML: same HTML as inner HTML of a span
        final Span labelIcon = new Span();
        labelIcon.getElement().setProperty("innerHTML", fontIcon.getHtml());
        labelIcon.setId(id);
        Tooltip.forComponent(labelIcon).setText(fontIcon.getDescription());
        labelIcon.addClassName("small");
        labelIcon.addClassName("font-icon");
        // space-separated and empty style names, as Vaadin 8 accepted them
        labelIcon.addClassNames(StringUtils.tokenizeToStringArray(fontIcon.getStyle(), " "));

        return labelIcon;
    }

    /**
     * Generate a label
     * 
     * @param i18n
     *            message source for internationalization
     * @param key
     *            to generate the internationalized name from
     * @return the label
     */
    public static Span generateLabel(final VaadinMessageSource i18n, final String key) {
        return new LabelBuilder().name(i18n.getMessage(key)).buildLabel();
    }

    /**
     * Generate a caption label
     * 
     * @param i18n
     *            message source for internationalization
     * @param key
     *            to generate the internationalized name from
     * @return the caption label
     */
    public static Span generateCaptionLabel(final VaadinMessageSource i18n, final String key) {
        return new LabelBuilder().name(i18n.getMessage(key)).buildCaptionLabel();
    }

    /**
     * Create generic combobox with backend data provider.
     * 
     * @param id
     *            id of the combobox
     * @param caption
     *            caption of the combobox
     * @param description
     *            description of the combobox
     * @param placeholder
     *            placeholder of the combobox
     * @param isEmptySelectionAllowed
     *            flag for empty selection enabled
     * @param itemCaptionGenerator
     *            generator fot item captions
     * @param dataProvider
     *            provides data/items
     * 
     * @return generic ComboBox
     */
    public static <T> ComboBox<T> getComboBox(final String id, final String caption, final String description,
            final String placeholder, final boolean isEmptySelectionAllowed,
            final ItemLabelGenerator<T> itemCaptionGenerator, final DataProvider<T, String> dataProvider) {
        final ComboBox<T> comboBox = getComboBox(id, caption, description, placeholder, isEmptySelectionAllowed,
                itemCaptionGenerator);
        comboBox.setItems(dataProvider);

        return comboBox;
    }

    private static <T> ComboBox<T> getComboBox(final String id, final String caption, final String description,
            final String placeholder, final boolean isEmptySelectionAllowed,
            final ItemLabelGenerator<T> itemCaptionGenerator) {
        final ComboBox<T> comboBox = new ComboBox<>();
        // Vaadin 8 accepted a null id (no id); Flow's setId throws on null
        if (id != null) {
            comboBox.setId(id);
        }
        comboBox.setLabel(caption);
        comboBox.setTooltipText(description);
        comboBox.setPlaceholder(placeholder);
        comboBox.addThemeVariants(ComboBoxVariant.LUMO_SMALL);
        // Vaadin 8 empty selection item: clearing the value in Flow
        comboBox.setClearButtonVisible(isEmptySelectionAllowed);

        comboBox.setItemLabelGenerator(itemCaptionGenerator);

        return comboBox;
    }

    /**
     * Create generic combobox with list in-memory data provider.
     * 
     * @param id
     *            id of the combobox
     * @param caption
     *            caption of the combobox
     * @param description
     *            description of the combobox
     * @param placeholder
     *            placeholder of the combobox
     * @param isEmptySelectionAllowed
     *            flag for empty selection enabled
     * @param itemCaptionGenerator
     *            generator fot item captions
     * @param dataProvider
     *            provides data/items
     * 
     * @return generic ComboBox
     */
    public static <T> ComboBox<T> getComboBox(final String id, final String caption, final String description,
            final String placeholder, final boolean isEmptySelectionAllowed,
            final ItemLabelGenerator<T> itemCaptionGenerator, final ListDataProvider<T> dataProvider) {
        final ComboBox<T> comboBox = getComboBox(id, caption, description, placeholder, isEmptySelectionAllowed,
                itemCaptionGenerator);
        comboBox.setItems(dataProvider);

        return comboBox;
    }
}
