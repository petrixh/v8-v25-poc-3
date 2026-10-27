/**
 * Copyright (c) 2018 Bosch Software Innovations GmbH and others
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.hawkbit.ui.tenantconfiguration.repository;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.hawkbit.repository.model.Action.Status;
import org.eclipse.hawkbit.repository.model.TenantConfiguration;
import org.eclipse.hawkbit.ui.common.builder.TextFieldBuilder;
import org.eclipse.hawkbit.ui.common.data.proxies.ProxySystemConfigRepository;
import org.eclipse.hawkbit.ui.components.SPUIComponentProvider;
import org.eclipse.hawkbit.ui.utils.UIComponentIdProvider;
import org.eclipse.hawkbit.ui.utils.VaadinMessageSource;

import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.data.binder.ValidationResult;
import com.vaadin.flow.data.provider.DataProvider;
import com.vaadin.flow.data.validator.IntegerRangeValidator;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;

/**
 * This class represents the UI item for configuring automatic action cleanup in
 * the Repository Configuration section of the System Configuration view.
 */
public class ActionAutoCleanupConfigurationItem extends VerticalLayout {

    private static final long serialVersionUID = 1L;

    private static final int MAX_EXPIRY_IN_DAYS = 1000;

    private static final String MSG_KEY_PREFIX = "label.configuration.repository.autocleanup.action.prefix";
    private static final String MSG_KEY_BODY = "label.configuration.repository.autocleanup.action.body";
    private static final String MSG_KEY_SUFFIX = "label.configuration.repository.autocleanup.action.suffix";
    private static final String MSG_KEY_INVALID_EXPIRY = "label.configuration.repository.autocleanup.action.expiry.invalid";
    private static final String MSG_KEY_NOTICE = "label.configuration.repository.autocleanup.action.notice";

    private static final Collection<ActionStatusOption> ACTION_STATUS_OPTIONS = Arrays.asList(
            new ActionStatusOption(Status.CANCELED), new ActionStatusOption(Status.ERROR),
            new ActionStatusOption(Status.CANCELED, Status.ERROR));

    private final VerticalLayout container;
    private final ComboBox<ActionStatusOption> actionStatusCombobox;
    private final TextField actionExpiryInput;

    private final VaadinMessageSource i18n;

    /**
     * Constructs the Action Cleanup configuration UI.
     *
     * @param binder
     *            ProxySystemConfigWindow binder
     * @param i18n
     *            VaadinMessageSource
     */
    public ActionAutoCleanupConfigurationItem(final Binder<ProxySystemConfigRepository> binder,
            final VaadinMessageSource i18n) {
        this.i18n = i18n;
        this.setSpacing(false);
        this.setPadding(false);
        add(SPUIComponentProvider.generateLabel(i18n, "label.configuration.repository.autocleanup.action"));
        container = new VerticalLayout();
        container.setSpacing(false);
        container.setPadding(false);
        final HorizontalLayout row1 = newHorizontalLayout();
        actionStatusCombobox = SPUIComponentProvider.getComboBox(
                UIComponentIdProvider.SYSTEM_CONFIGURATION_ACTION_CLEANUP_ACTION_TYPES, null, null, null, false,
                ActionStatusOption::getName, DataProvider.ofCollection(ACTION_STATUS_OPTIONS));
        actionStatusCombobox.removeClassNames("small");
        actionStatusCombobox.addClassNames("tiny");
        actionStatusCombobox.setWidth("200px");
        binder.bind(actionStatusCombobox, ProxySystemConfigRepository::getActionCleanupStatus,
                ProxySystemConfigRepository::setActionCleanupStatus);
        actionExpiryInput = new TextFieldBuilder(TenantConfiguration.VALUE_MAX_SIZE).buildTextComponent();
        actionExpiryInput.setId(UIComponentIdProvider.SYSTEM_CONFIGURATION_ACTION_CLEANUP_ACTION_EXPIRY);
        actionExpiryInput.setWidth("55px");
        binder.forField(actionExpiryInput).asRequired(i18n.getMessage(MSG_KEY_INVALID_EXPIRY))
                .withValidator((value, context) -> {
                    try {
                        return new IntegerRangeValidator(i18n.getMessage(MSG_KEY_INVALID_EXPIRY), 1, MAX_EXPIRY_IN_DAYS)
                                .apply(Integer.parseInt(value), context);
                    } catch (final NumberFormatException ex) {
                        return ValidationResult.error(i18n.getMessage(MSG_KEY_INVALID_EXPIRY));
                    }
                }).bind(ProxySystemConfigRepository::getActionExpiryDays, ProxySystemConfigRepository::setActionExpiryDays);

        row1.add(newLabel(MSG_KEY_PREFIX));
        row1.add(actionStatusCombobox);
        row1.add(newLabel(MSG_KEY_BODY));
        row1.add(actionExpiryInput);
        row1.add(newLabel(MSG_KEY_SUFFIX));
        container.add(row1);

        final HorizontalLayout row2 = newHorizontalLayout();
        row2.add(newLabel(MSG_KEY_NOTICE));
        container.add(row2);
        if (binder.getBean().isActionAutocleanup()) {
            showSettings();
        }
    }

    private Span newLabel(final String msgKey) {
        final Span label = SPUIComponentProvider.generateLabel(i18n, msgKey);
        label.setWidth(null);
        return label;
    }

    private static HorizontalLayout newHorizontalLayout() {
        final HorizontalLayout layout = new HorizontalLayout();
        layout.setSpacing(true);
        return layout;
    }

    /**
     * Show action auto cleanup settings
     */
    public void showSettings() {
        add(container);
    }

    /**
     * Hide action auto cleanup settings
     */
    public void hideSettings() {
        remove(container);
    }

    /**
     * @return Action status option of auto cleanup
     */
    public static Collection<ActionStatusOption> getActionStatusOptions() {
        return ACTION_STATUS_OPTIONS;
    }

    /**
     * Auto cleanup action status options
     */
    public static class ActionStatusOption implements Serializable {
        private static final long serialVersionUID = 1L;

        private static final CharSequence SEPARATOR = " + ";
        private final Set<Status> statusSet;
        private String name;

        /**
         * Constructor for ActionStatusOption
         *
         * @param status
         *            Action status
         */
        public ActionStatusOption(final Status... status) {
            statusSet = Arrays.stream(status).collect(Collectors.toCollection(() -> EnumSet.noneOf(Status.class)));
        }

        /**
         * @return Status name
         */
        public String getName() {
            if (name == null) {
                name = assembleName();
            }
            return name;
        }

        /**
         * @return List of status
         */
        public Set<Status> getStatus() {
            return statusSet;
        }

        /**
         * @return Join action status
         */
        private String assembleName() {
            return statusSet.stream().map(Status::name).collect(Collectors.joining(SEPARATOR));
        }
    }
}
