/**
 * Copyright (c) 2020 Bosch.IO GmbH and others
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.hawkbit.ui.filtermanagement;

import org.eclipse.hawkbit.repository.rsql.RsqlValidationOracle;
import org.eclipse.hawkbit.ui.UiProperties;
import org.eclipse.hawkbit.ui.common.builder.FormComponentBuilder;
import org.eclipse.hawkbit.ui.common.data.proxies.ProxyTargetFilterQuery;
import org.eclipse.hawkbit.ui.components.SPUIComponentProvider;
import org.eclipse.hawkbit.ui.decorators.SPUIButtonStyleNoBorder;
import org.eclipse.hawkbit.ui.utils.UIComponentIdProvider;
import org.eclipse.hawkbit.ui.utils.UIMessageIdProvider;
import org.eclipse.hawkbit.ui.utils.VaadinMessageSource;

import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.data.binder.ValidationResult;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.textfield.TextField;

/**
 * Builder for target filter add update layout component
 */
public class TargetFilterAddUpdateLayoutComponentBuilder {

    private final VaadinMessageSource i18n;
    private final UiProperties uiProperties;
    private final RsqlValidationOracle rsqlValidationOracle;

    /**
     * Constructor for TargetFilterAddUpdateLayoutComponentBuilder
     *
     * @param i18n
     *            VaadinMessageSource
     * @param uiProperties
     *            UiProperties
     * @param rsqlValidationOracle
     *            RsqlValidationOracle
     */
    public TargetFilterAddUpdateLayoutComponentBuilder(final VaadinMessageSource i18n, final UiProperties uiProperties,
            final RsqlValidationOracle rsqlValidationOracle) {
        this.i18n = i18n;
        this.uiProperties = uiProperties;
        this.rsqlValidationOracle = rsqlValidationOracle;
    }

    /**
     * create name field
     * 
     * @param binder
     *            binder the input will be bound to
     * @return input component
     */
    public TextField createNameField(final Binder<ProxyTargetFilterQuery> binder) {
        final TextField filterName = FormComponentBuilder
                .createNameInput(binder, i18n, UIComponentIdProvider.CUSTOM_FILTER_ADD_NAME).getComponent();
        filterName.setWidth("40%");

        return filterName;
    }

    /**
     * Create query field
     *
     * @param binder
     *            Vaadin binder
     *
     * @return Auto complete query field component
     */
    public AutoCompleteTextFieldComponent createQueryField(final Binder<ProxyTargetFilterQuery> binder) {
        final AutoCompleteTextFieldComponent autoCompleteComponent = new AutoCompleteTextFieldComponent(
                rsqlValidationOracle);

        binder.forField(autoCompleteComponent)
                .withValidator((query, context) -> autoCompleteComponent.isValid() ? ValidationResult.ok()
                        : ValidationResult
                                .error(i18n.getMessage(UIMessageIdProvider.MESSAGE_FILTER_QUERY_ERROR_NOTVALIDE)))
                .bind(ProxyTargetFilterQuery::getQuery, ProxyTargetFilterQuery::setQuery);

        return autoCompleteComponent;
    }

    /**
     * Create filter help link
     *
     * @return Filter help link
     */
    public Anchor createFilterHelpLink() {
        return SPUIComponentProvider.getHelpLink(i18n,
                uiProperties.getLinks().getDocumentation().getTargetfilterView());
    }

    /**
     * Create filter to search targets
     *
     * @return Filter button to search targets
     */
    public Button createSearchTargetsByFilterButton() {
        return SPUIComponentProvider.getButton(UIComponentIdProvider.FILTER_SEARCH_ICON_ID, "",
                i18n.getMessage(UIMessageIdProvider.TOOLTIP_SEARCH), null, false, VaadinIcon.SEARCH,
                SPUIButtonStyleNoBorder.class);
    }

    /**
     * Create save button
     *
     * @return Save button
     */
    public Button createSaveButton() {
        // icon only (Vaadin 8: the ICON_ONLY style hid the caption); the id is not the text; the tooltip text is
        // the accessible name
        final Button saveButton = SPUIComponentProvider.getButton(UIComponentIdProvider.CUSTOM_FILTER_SAVE_ICON,
                "", i18n.getMessage(UIMessageIdProvider.TOOLTIP_SAVE), null,
                false, VaadinIcon.HARDDRIVE, SPUIButtonStyleNoBorder.class);
        saveButton.setAriaLabel(i18n.getMessage(UIMessageIdProvider.TOOLTIP_SAVE));
        return saveButton;
    }
}
