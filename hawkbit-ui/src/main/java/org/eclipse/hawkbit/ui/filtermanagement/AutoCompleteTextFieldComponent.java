/**
 * Copyright (c) 2015 Bosch Software Innovations GmbH and others
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.hawkbit.ui.filtermanagement;

import java.util.LinkedList;
import java.util.List;

import org.eclipse.hawkbit.repository.model.TargetFilterQuery;
import org.eclipse.hawkbit.repository.rsql.RsqlValidationOracle;
import org.eclipse.hawkbit.repository.rsql.ValidationOracleContext;
import org.eclipse.hawkbit.ui.common.builder.TextFieldBuilder;
import org.eclipse.hawkbit.ui.utils.SPUIStyleDefinitions;
import org.eclipse.hawkbit.ui.utils.UIComponentIdProvider;

import com.vaadin.flow.component.AbstractField.ComponentValueChangeEvent;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.shared.Tooltip;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.component.customfield.CustomField;
import com.vaadin.flow.component.orderedlayout.FlexComponent.Alignment;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.textfield.TextField;

/**
 * An textfield with the {@link TextFieldSuggestionBox} extension which shows
 * suggestions in a suggestion-pop-up window while typing.
 */
public class AutoCompleteTextFieldComponent extends CustomField<String> {
    private static final long serialVersionUID = 1L;

    private final transient RsqlValidationOracle rsqlValidationOracle;

    private final transient List<ValidationListener> listeners = new LinkedList<>();

    private final Span validationIcon;
    private final Tooltip validationIconTooltip;
    private final TextField queryTextField;
    private final HorizontalLayout autoCompleteLayout;

    private boolean isValid;
    private String targetFilterQuery;

    /**
     * Constructor for AutoCompleteTextFieldComponent
     *
     * @param rsqlValidationOracle
     *            RsqlValidationOracle
     */
    public AutoCompleteTextFieldComponent(final RsqlValidationOracle rsqlValidationOracle) {
        this.rsqlValidationOracle = rsqlValidationOracle;

        this.validationIcon = createStatusIcon();
        this.validationIconTooltip = Tooltip.forComponent(validationIcon);
        this.queryTextField = createSearchField();
        this.autoCompleteLayout = new HorizontalLayout();

        this.isValid = false;
        this.targetFilterQuery = "";

        init();
    }

    private void init() {
        autoCompleteLayout.setSizeFull();
        autoCompleteLayout.setSpacing(false);
        autoCompleteLayout.setPadding(false);
        autoCompleteLayout.addClassNames("custom-search-layout");

        autoCompleteLayout.add(validationIcon);
        autoCompleteLayout.setVerticalComponentAlignment(Alignment.CENTER, validationIcon);

        autoCompleteLayout.add(queryTextField);
        autoCompleteLayout.setFlexGrow(1.0F, queryTextField);

        queryTextField.addValueChangeListener(event -> onQueryFilterChange(event.getValue()));

        new TextFieldSuggestionBox(rsqlValidationOracle).extend(queryTextField);

        // Vaadin 8 initContent(): Flow's CustomField holds its content directly
        add(autoCompleteLayout);
    }

    @Override
    protected void setPresentationValue(final String value) {
        if (value == null) {
            queryTextField.setValue("");
            hideValidationIcon();

            targetFilterQuery = "";
            isValid = false;
        } else {
            queryTextField.setValue(value);
        }
    }

    @Override
    protected String generateModelValue() {
        return targetFilterQuery;
    }

    /**
     * Add changed listener to text field
     *
     * @param listener
     *            Value change listener
     */
    public void addTextfieldChangedListener(
            final ValueChangeListener<ComponentValueChangeEvent<TextField, String>> listener) {
        queryTextField.addValueChangeListener(listener);
    }

    private static Span createStatusIcon() {
        // Vaadin 8: a Label with the icon's HTML; here a span holding the icon
        final Span statusIcon = new Span(VaadinIcon.CHECK_CIRCLE.create());
        statusIcon.setId(UIComponentIdProvider.VALIDATION_STATUS_ICON_ID);
        statusIcon.setVisible(false);

        return statusIcon;
    }

    private static TextField createSearchField() {
        final TextField textField = new TextFieldBuilder(TargetFilterQuery.QUERY_MAX_SIZE)
                .id(UIComponentIdProvider.CUSTOM_FILTER_QUERY).buildTextComponent();
        textField.setWidthFull();
        textField.addClassNames("target-filter-textfield");

        textField.setValueChangeMode(ValueChangeMode.EAGER);

        return textField;
    }

    /**
     * Clears the textfield and resets the validation icon.
     */
    @Override
    public void clear() {
        queryTextField.clear();
        hideValidationIcon();
    }

    @Override
    public void focus() {
        queryTextField.focus();
    }

    /**
     * Adds the given listener
     * 
     * @param validationListener
     *            the listener to be called in case of validation status change
     */
    public void addValidationListener(final ValidationListener validationListener) {
        listeners.add(validationListener);
    }

    private void onQueryFilterChange(final String newQuery) {
        final ValidationOracleContext validationContext = rsqlValidationOracle.suggest(newQuery, newQuery.length());
        final boolean valid = !validationContext.isSyntaxError();
        final String message = valid ? newQuery : validationContext.getSyntaxErrorContext().getErrorMessage();

        targetFilterQuery = newQuery;
        isValid = valid;

        if (valid) {
            showValidationSuccesIcon();
        } else {
            showValidationFailureIcon(message);
        }

        // Flow: the field's value is read from generateModelValue()
        updateValue();
        listeners.forEach(listener -> listener.validationChanged(valid, message));
    }

    private void hideValidationIcon() {
        validationIcon.setVisible(false);
    }

    private void showValidationSuccesIcon() {
        validationIcon.setVisible(true);
        validationIcon.removeAll();
        validationIcon.add(VaadinIcon.CHECK_CIRCLE.create());
        validationIcon.setClassName(SPUIStyleDefinitions.SUCCESS_ICON);
        validationIconTooltip.setText(null);
    }

    private void showValidationFailureIcon(final String validationMessage) {
        validationIcon.setVisible(true);
        validationIcon.removeAll();
        validationIcon.add(VaadinIcon.CLOSE_CIRCLE.create());
        validationIcon.setClassName(SPUIStyleDefinitions.ERROR_ICON);
        validationIconTooltip.setText(validationMessage);
    }

    /**
     * @return True if auto complete component else false
     */
    public boolean isValid() {
        return isValid;
    }

    /**
     * Change listener on the textfield.
     */
    @FunctionalInterface
    public interface ValidationListener {
        /**
         * Called when the text has been changed and validated.
         * 
         * @param valid
         *            indicates if the entered query text is valid
         * @param validationMessage
         *            the entered query text
         */
        void validationChanged(final boolean valid, final String validationMessage);
    }
}
