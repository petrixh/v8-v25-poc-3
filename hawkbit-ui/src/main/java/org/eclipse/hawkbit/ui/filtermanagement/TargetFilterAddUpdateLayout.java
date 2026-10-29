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
import org.eclipse.hawkbit.ui.SpPermissionChecker;
import org.eclipse.hawkbit.ui.UiProperties;
import org.eclipse.hawkbit.ui.common.AbstractEntityWindowLayout;
import org.eclipse.hawkbit.ui.common.CommonDialogWindow.SaveDialogCloseListener;
import org.eclipse.hawkbit.ui.common.data.proxies.ProxyTarget;
import org.eclipse.hawkbit.ui.common.data.proxies.ProxyTargetFilterQuery;
import org.eclipse.hawkbit.ui.common.event.EventTopics;
import org.eclipse.hawkbit.ui.common.event.EventView;
import org.eclipse.hawkbit.ui.common.event.FilterChangedEventPayload;
import org.eclipse.hawkbit.ui.common.event.FilterType;
import org.eclipse.hawkbit.ui.filtermanagement.state.TargetFilterDetailsLayoutUiState;
import org.eclipse.hawkbit.ui.utils.SPUIStyleDefinitions;
import org.eclipse.hawkbit.ui.utils.VaadinMessageSource;
import org.vaadin.spring.events.EventBus.UIEventBus;

import com.vaadin.flow.component.HasValue.ValueChangeEvent;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.Shortcuts;
import com.vaadin.flow.shared.Registration;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.formlayout.FormLayout.ResponsiveStep;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.orderedlayout.FlexComponent.Alignment;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.textfield.TextField;

/**
 * Target add/update window layout.
 */
public class TargetFilterAddUpdateLayout extends AbstractEntityWindowLayout<ProxyTargetFilterQuery> {
    private final VaadinMessageSource i18n;
    private final SpPermissionChecker permChecker;

    private static final String FILTER_QUERY_CAPTION = "textfield.query";

    private final TargetFilterAddUpdateLayoutComponentBuilder filterComponentBuilder;

    private final TextField filterNameInput;
    private final AutoCompleteTextFieldComponent autoCompleteComponent;
    private final Anchor helpLink;
    private final Button searchButton;
    private final Button saveButton;
    private final TargetFilterDetailsLayoutUiState uiState;
    private final UIEventBus eventBus;

    private Registration saveListener;

    /**
     * Constructor for AbstractTagWindowLayout
     * 
     * @param i18n
     *            VaadinMessageSource
     * @param permChecker
     *            Permission checker
     * @param uiProperties
     *            UiProperties
     * @param uiState
     *            TargetFilterDetailsLayoutUiState
     * @param eventBus
     *            UIEventBus
     * @param rsqlValidationOracle
     *            RsqlValidationOracle
     */
    protected TargetFilterAddUpdateLayout(final VaadinMessageSource i18n, final SpPermissionChecker permChecker,
            final UiProperties uiProperties, final TargetFilterDetailsLayoutUiState uiState, final UIEventBus eventBus,
            final RsqlValidationOracle rsqlValidationOracle) {
        super();

        this.i18n = i18n;
        this.permChecker = permChecker;
        this.uiState = uiState;
        this.eventBus = eventBus;
        this.filterComponentBuilder = new TargetFilterAddUpdateLayoutComponentBuilder(i18n, uiProperties,
                rsqlValidationOracle);

        this.filterNameInput = filterComponentBuilder.createNameField(binder);
        this.autoCompleteComponent = filterComponentBuilder.createQueryField(binder);
        this.helpLink = filterComponentBuilder.createFilterHelpLink();
        this.searchButton = filterComponentBuilder.createSearchTargetsByFilterButton();
        this.saveButton = filterComponentBuilder.createSaveButton();

        addValueChangeListeners();
    }

    @Override
    public Component getRootComponent() {
        final FormLayout formLayout = new FormLayout();
        // Vaadin 8 FormLayout: one column, spacing on, no margin (Flow's
        // FormLayout has no spacing/margin switches)
        formLayout.setResponsiveSteps(new ResponsiveStep("0", 1));
        formLayout.setWidthFull();

        formLayout.add(filterNameInput);
        autoCompleteComponent.setLabel(i18n.getMessage(FILTER_QUERY_CAPTION));
        autoCompleteComponent.setRequiredIndicatorVisible(true);
        // Vaadin 8 called focus() here before the field was attached, which did nothing: the form opens with
        // nothing focused, so there is no focus() call
        formLayout.add(autoCompleteComponent);

        final HorizontalLayout actionsLayout = new HorizontalLayout();
        actionsLayout.setSpacing(false);
        actionsLayout.setPadding(false);
        actionsLayout.setSizeUndefined();
        actionsLayout.addClassNames(SPUIStyleDefinitions.ADD_UPDATE_FILTER_ACTIONS_LAYOUT);

        actionsLayout.add(helpLink);
        searchButton.setEnabled(false);
        actionsLayout.add(searchButton);
        saveButton.setEnabled(false);
        actionsLayout.add(saveButton);

        final HorizontalLayout filterQueryLayout = new HorizontalLayout();
        filterQueryLayout.setSpacing(true);
        filterQueryLayout.setPadding(false);
        filterQueryLayout.setWidth("90%");
        filterQueryLayout.addClassNames(SPUIStyleDefinitions.ADD_UPDATE_FILTER_LAYOUT);

        filterQueryLayout.add(formLayout);
        filterQueryLayout.setFlexGrow(1.0F, formLayout);

        filterQueryLayout.add(actionsLayout);
        filterQueryLayout.setVerticalComponentAlignment(Alignment.END, actionsLayout);

        return filterQueryLayout;
    }

    private void addValueChangeListeners() {
        searchButton.addClickListener(event -> onSearchIconClick());
        autoCompleteComponent.addValidationListener((valid, message) -> searchButton.setEnabled(valid));
        autoCompleteComponent.addTextfieldChangedListener(this::onFilterQueryTextfieldChanged);
        // "List Filtered Targets": Enter while the focus is in the query field
        Shortcuts.addShortcutListener(autoCompleteComponent, this::onSearchIconClick, Key.ENTER)
                .listenOn(autoCompleteComponent);
        filterNameInput.addValueChangeListener(this::onFilterNameChanged);

        if (permChecker.hasUpdateTargetPermission()) {
            addValidationListener(saveButton::setEnabled);
        }
    }

    private void onFilterQueryTextfieldChanged(final ValueChangeEvent<String> event) {
        uiState.setFilterQueryValueInput(event.getValue());
    }

    private void onFilterNameChanged(final ValueChangeEvent<String> event) {
        uiState.setNameInput(event.getValue());
    }

    private void onSearchIconClick() {
        if (autoCompleteComponent.isValid()) {
            filterTargetListByQuery(autoCompleteComponent.getValue());
        }
    }

    /**
     * Filter target list by query
     *
     * @param query
     *            Input query value
     */
    public void filterTargetListByQuery(final String query) {
        eventBus.publish(EventTopics.FILTER_CHANGED, this,
                new FilterChangedEventPayload<>(ProxyTarget.class, FilterType.QUERY, query, EventView.TARGET_FILTER));
    }

    /**
     * Save the changes
     *
     * @param saveCallback
     *            SaveDialogCloseListener
     */
    public void setSaveCallback(final SaveDialogCloseListener saveCallback) {
        if (saveListener != null) {
            saveListener.remove();
        }

        saveListener = saveButton.addClickListener(event -> {
            if (!saveCallback.canWindowSaveOrUpdate()) {
                return;
            }

            saveCallback.saveOrUpdate();
        });
    }

    /**
     * Check validity of target filter query.
     * 
     * @return {@code true}: if the target filter query is valid {@code false}:
     *         otherwise
     */
    public boolean isFilterQueryValid() {
        return autoCompleteComponent.isValid();
    }
}
