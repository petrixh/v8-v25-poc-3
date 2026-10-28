/**
 * Copyright (c) 2020 Bosch.IO GmbH and others
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.hawkbit.ui.common.grid.header.support;

import java.util.function.Consumer;
import java.util.function.Supplier;

import org.eclipse.hawkbit.ui.common.builder.TextFieldBuilder;
import org.eclipse.hawkbit.ui.components.SPUIComponentProvider;
import org.eclipse.hawkbit.ui.decorators.SPUIButtonStyleNoBorder;
import org.eclipse.hawkbit.ui.utils.SPUIDefinitions;
import org.eclipse.hawkbit.ui.utils.UIMessageIdProvider;
import org.eclipse.hawkbit.ui.utils.VaadinMessageSource;
import org.springframework.util.StringUtils;

import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent.Alignment;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.textfield.TextField;

/**
 * Header support for search functionality
 */
public class SearchHeaderSupport implements HeaderSupport {
    private static final String MODE_SEARCH_INPUT = "mode-search-input";

    private final VaadinMessageSource i18n;

    private final String searchFieldId;
    private final String searchResetIconId;
    private final Supplier<String> searchStateSupplier;
    private final Consumer<String> searchByCallback;

    private final TextField searchField;
    private final Button searchResetIcon;

    private boolean isSearchInputActive;

    /**
     * Constructor for SearchHeaderSupport
     *
     * @param i18n
     *            VaadinMessageSource
     * @param searchFieldId
     *            Search field id
     * @param searchResetIconId
     *            Value supplier for search box
     * @param searchStateSupplier
     *            Search state supplier
     * @param searchByCallback
     *            Callback for search event
     */
    public SearchHeaderSupport(final VaadinMessageSource i18n, final String searchFieldId,
            final String searchResetIconId, final Supplier<String> searchStateSupplier,
            final Consumer<String> searchByCallback) {
        this.i18n = i18n;

        this.searchFieldId = searchFieldId;
        this.searchResetIconId = searchResetIconId;
        this.searchStateSupplier = searchStateSupplier;
        this.searchByCallback = searchByCallback;

        this.searchField = createSearchField();
        this.searchResetIcon = createSearchResetIcon();
    }

    private TextField createSearchField() {
        final TextField searchInput = new TextFieldBuilder(64).id(searchFieldId).style(SPUIDefinitions.FILTER_BOX)
                .styleName("text-style").buildTextComponent();
        searchInput.setWidthFull();

        searchInput.addValueChangeListener(event -> {
            // we do not want to send the event during state restore, so we
            // react only on user input
            if (event.isFromClient()) {
                searchByCallback.accept(event.getValue());
            }
        });

        return searchInput;
    }

    private Button createSearchResetIcon() {
        final Button searchResetButton = SPUIComponentProvider.getButton(searchResetIconId, "",
                i18n.getMessage(UIMessageIdProvider.TOOLTIP_SEARCH), null, false, VaadinIcon.SEARCH,
                SPUIButtonStyleNoBorder.class);

        searchResetButton.addClickListener(event -> onSearchResetClick());

        return searchResetButton;
    }

    private void onSearchResetClick() {
        if (isSearchInputActive) {
            // Clicked on reset search icon
            closeSearchTextField();
        } else {
            // Clicked on search icon
            openSearchTextField();
        }
    }

    private void openSearchTextField() {
        searchResetIcon.setIcon(VaadinIcon.CLOSE.create());
        searchResetIcon.setTooltipText(i18n.getMessage(UIMessageIdProvider.TOOLTIP_RESET));
        searchResetIcon.addClassNames(SPUIDefinitions.FILTER_RESET_ICON, MODE_SEARCH_INPUT);

        searchField.setVisible(true);
        // on a fresh page load (deep link) the text field is not defined in the browser yet when a plain focus() runs
        searchField.getElement().executeJs("customElements.whenDefined('vaadin-text-field').then(() => this.focus());");

        isSearchInputActive = true;
    }

    private void closeSearchTextField() {
        searchResetIcon.setIcon(VaadinIcon.SEARCH.create());
        searchResetIcon.setTooltipText(i18n.getMessage(UIMessageIdProvider.TOOLTIP_SEARCH));
        searchResetIcon.removeClassNames(SPUIDefinitions.FILTER_RESET_ICON, MODE_SEARCH_INPUT);

        searchField.setValue("");
        searchField.setVisible(false);

        searchByCallback.accept(null);

        isSearchInputActive = false;
    }

    /**
     * Update the search and trigger the callback to refresh the grid.
     * 
     * @param searchQuery
     *            search query
     */
    public void setAndTriggerSearch(final String searchQuery) {
        if (!StringUtils.isEmpty(searchQuery)) {
            openSearchTextField();
            searchField.setValue(searchQuery);
            searchByCallback.accept(searchQuery);
        }
    }

    @Override
    public void restoreState() {
        final String onLoadSearchBoxValue = searchStateSupplier.get();

        if (!StringUtils.isEmpty(onLoadSearchBoxValue)) {
            openSearchTextField();
            searchField.setValue(onLoadSearchBoxValue);
        }
    }

    /**
     * Disable search icon
     */
    public void disableSearch() {
        searchResetIcon.setEnabled(false);
    }

    /**
     * Enable search icon
     */
    public void enableSearch() {
        searchResetIcon.setEnabled(true);
    }

    /**
     * Reset the search value to empty and close the search bar
     */
    public void resetSearch() {
        if (isSearchInputActive) {
            closeSearchTextField();
        }
    }

    @Override
    public Component getHeaderComponent() {
        final HorizontalLayout headerIconLayout = new HorizontalLayout();
        headerIconLayout.setPadding(false);
        headerIconLayout.setSpacing(false);
        headerIconLayout.setWidthFull();

        headerIconLayout.add(searchField);
        headerIconLayout.setVerticalComponentAlignment(Alignment.START, searchField);
        headerIconLayout.setFlexGrow(1.0F, searchField);

        headerIconLayout.add(searchResetIcon);
        headerIconLayout.setVerticalComponentAlignment(Alignment.START, searchResetIcon);
        headerIconLayout.setFlexGrow(0F, searchResetIcon);

        // hidden by default
        searchField.setVisible(false);

        return headerIconLayout;
    }

    @Override
    public float getExpandRation() {
        return 0.6F;
    }
}
