/**
 * Copyright (c) 2022 Bosch.IO GmbH and others
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.hawkbit.ui.management.targettable;

import static org.eclipse.hawkbit.ui.utils.UIComponentIdProvider.AUTO_CONFIRMATION_DETAILS_ACTIVATEDAT;
import static org.eclipse.hawkbit.ui.utils.UIComponentIdProvider.AUTO_CONFIRMATION_DETAILS_INITIATOR;
import static org.eclipse.hawkbit.ui.utils.UIComponentIdProvider.AUTO_CONFIRMATION_DETAILS_REMARK;
import static org.eclipse.hawkbit.ui.utils.UIComponentIdProvider.AUTO_CONFIRMATION_DETAILS_ROLLOUTS_USER;
import static org.eclipse.hawkbit.ui.utils.UIComponentIdProvider.AUTO_CONFIRMATION_DETAILS_STATE;
import static org.eclipse.hawkbit.ui.utils.UIComponentIdProvider.AUTO_CONFIRMATION_TOGGLE_DIALOG;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.eclipse.hawkbit.repository.ConfirmationManagement;
import org.eclipse.hawkbit.tenancy.TenantAware;
import org.eclipse.hawkbit.ui.SpPermissionChecker;
import org.eclipse.hawkbit.ui.UiProperties;
import org.eclipse.hawkbit.ui.common.CommonUiDependencies;
import org.eclipse.hawkbit.ui.common.ConfirmationDialog;
import org.eclipse.hawkbit.ui.common.data.proxies.ProxyKeyValueDetails;
import org.eclipse.hawkbit.ui.common.data.proxies.ProxyTargetConfirmationOptions;
import org.eclipse.hawkbit.ui.common.detailslayout.KeyValueDetailsComponent;
import org.eclipse.hawkbit.ui.components.SPUIComponentProvider;
import org.eclipse.hawkbit.ui.decorators.SPUIButtonStyleNoBorder;
import org.eclipse.hawkbit.ui.utils.SPDateTimeUtil;
import org.eclipse.hawkbit.ui.utils.UIComponentIdProvider;
import org.eclipse.hawkbit.ui.utils.VaadinMessageSource;
import org.springframework.util.StringUtils;

import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.customfield.CustomField;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.dialog.Dialog;

/**
 * target auto confirmation detail component
 */
public class TargetConfirmationOptionsComponent extends CustomField<ProxyTargetConfirmationOptions> {

    private static final long serialVersionUID = 1L;

    private final transient TargetAutoConfActivationWindowBuilder windowBuilder;
    private final transient ConfirmationManagement confirmationManagement;
    private final transient SpPermissionChecker permissionChecker;
    private final VaadinMessageSource i18n;
    private final HorizontalLayout targetConfirmationOptionsLayout;

    /**
     * Constructor for TargetConfirmationOptionsComponent
     *
     * @param commonUiDependencies
     *            the {@link CommonUiDependencies}
     * @param uiProperties
     *            the {@link UiProperties}
     * @param confirmationManagement
     *            the {@link ConfirmationManagement}
     * @param tenantAware
     *            the {@link TenantAware}
     */
    public TargetConfirmationOptionsComponent(final CommonUiDependencies commonUiDependencies,
            final UiProperties uiProperties, final ConfirmationManagement confirmationManagement,
            final TenantAware tenantAware) {
        this.i18n = commonUiDependencies.getI18n();
        this.confirmationManagement = confirmationManagement;
        this.permissionChecker = commonUiDependencies.getPermChecker();
        this.windowBuilder = new TargetAutoConfActivationWindowBuilder(commonUiDependencies, uiProperties, tenantAware,
                confirmationManagement);

        this.targetConfirmationOptionsLayout = new HorizontalLayout();
        this.targetConfirmationOptionsLayout.setSpacing(true);
        this.targetConfirmationOptionsLayout.setPadding(false);
        this.targetConfirmationOptionsLayout.setSizeFull();
        // Vaadin 8: initContent()
        add(targetConfirmationOptionsLayout);
    }

    @Override
    public ProxyTargetConfirmationOptions getValue() {
        // keep returning same value since reloading (triggering 'setPresentationValue' method)
        // will be done on button click.
        return new ProxyTargetConfirmationOptions();
    }

    @Override
    protected ProxyTargetConfirmationOptions generateModelValue() {
        return new ProxyTargetConfirmationOptions();
    }

    @Override
    protected void setPresentationValue(final ProxyTargetConfirmationOptions targetConfirmationOptions) {
        targetConfirmationOptionsLayout.removeAll();

        if (targetConfirmationOptions == null) {
            return;
        }

        final boolean isAutoConfirmationEnabled = targetConfirmationOptions.isAutoConfirmationEnabled();

        if (isAutoConfirmationEnabled) {
            final KeyValueDetailsComponent detailsLayout = buildAutoConfirmationDetailsLayout(
                    targetConfirmationOptions);
            targetConfirmationOptionsLayout.add(detailsLayout);
            targetConfirmationOptionsLayout.setFlexGrow(1.0F, detailsLayout);
        } else {
            final KeyValueDetailsComponent component = toKeyValueDetailsComponent(
                    Collections.singletonList(new ProxyKeyValueDetails(AUTO_CONFIRMATION_DETAILS_STATE,
                            i18n.getMessage("label.target.auto.confirmation.state"),
                            i18n.getMessage("label.target.auto.confirmation.deactivated"))));

            targetConfirmationOptionsLayout.add(component);
            targetConfirmationOptionsLayout.setFlexGrow(1.0F, component);
        }

        // do only provide toggle button when having permission for that
        if (permissionChecker.hasUpdateTargetPermission()) {
            final Button button = buildAutoConfirmationToggleButton(targetConfirmationOptions);
            targetConfirmationOptionsLayout.add(button);
        }
    }

    private KeyValueDetailsComponent buildAutoConfirmationDetailsLayout(final ProxyTargetConfirmationOptions options) {
        final List<ProxyKeyValueDetails> values = new ArrayList<>();
        values.add(new ProxyKeyValueDetails(AUTO_CONFIRMATION_DETAILS_STATE,
                i18n.getMessage("label.target.auto.confirmation.state"),
                i18n.getMessage("label.target.auto.confirmation.active")));

        if (StringUtils.hasText(options.getInitiator())) {
            values.add(new ProxyKeyValueDetails(AUTO_CONFIRMATION_DETAILS_INITIATOR,
                    i18n.getMessage("label.target.auto.confirmation.initiator"), options.getInitiator()));
        }
        values.add(new ProxyKeyValueDetails(AUTO_CONFIRMATION_DETAILS_ROLLOUTS_USER,
                i18n.getMessage("label.target.auto.confirmation.systemuser"), options.getInitiatedSystemUser()));
        if (StringUtils.hasText(options.getRemark())) {
            values.add(new ProxyKeyValueDetails(AUTO_CONFIRMATION_DETAILS_REMARK,
                    i18n.getMessage("label.target.auto.confirmation.remark"), options.getRemark()));
        }
        values.add(new ProxyKeyValueDetails(AUTO_CONFIRMATION_DETAILS_ACTIVATEDAT,
                i18n.getMessage("label.target.auto.confirmation.activatedat"),
                SPDateTimeUtil.getFormattedDate(options.getActivatedAt())));

        return toKeyValueDetailsComponent(values);
    }

    private static KeyValueDetailsComponent toKeyValueDetailsComponent(final List<ProxyKeyValueDetails> values) {
        final KeyValueDetailsComponent details = new KeyValueDetailsComponent();
        details.disableSpacing();
        details.setValue(values);
        return details;
    }

    private Button buildAutoConfirmationToggleButton(final ProxyTargetConfirmationOptions options) {
        final Button toggleAutoConfirmationButton;

        if (options.isAutoConfirmationEnabled()) {
            toggleAutoConfirmationButton = SPUIComponentProvider.getButton(
                    UIComponentIdProvider.AUTO_CONFIRMATION_DETAILS_TOGGLE, "",
                    i18n.getMessage("button.target.auto.confirmation.disable"), "huge", true,
                    // FontAwesome.TOGGLE_ON: no toggle icon in VaadinIcon (A11)
                    VaadinIcon.CHECK_SQUARE, SPUIButtonStyleNoBorder.class);
        } else {
            toggleAutoConfirmationButton = SPUIComponentProvider.getButton(
                    UIComponentIdProvider.AUTO_CONFIRMATION_DETAILS_TOGGLE, "",
                    i18n.getMessage("button.target.auto.confirmation.activate"), "huge", true,
                    // FontAwesome.TOGGLE_OFF: no toggle icon in VaadinIcon (A11)
                    VaadinIcon.CHECK_SQUARE_O, SPUIButtonStyleNoBorder.class);
        }

        toggleAutoConfirmationButton.addClickListener(e -> {
            if (options.isAutoConfirmationEnabled()) {
                final ConfirmationDialog dialog = ConfirmationDialog
                        .newBuilder(i18n, AUTO_CONFIRMATION_TOGGLE_DIALOG).icon(VaadinIcon.WARNING)
                        .caption(i18n.getMessage("caption.target.auto.confirmation.disable"))
                        .question(i18n.getMessage("message.target.auto.confirmation.disable")).onSaveOrUpdate(() -> {
                            confirmationManagement.deactivateAutoConfirmation(options.getControllerId());
                            setPresentationValue(ProxyTargetConfirmationOptions.disabled(options.getControllerId()));
                        }).build();

                // Flow: the dialog opened last is on top (no bringToFront)
                dialog.getWindow().open();
            } else {
                final Dialog window = windowBuilder.getWindowForUpdate(options);
                window.open();
                window.setVisible(Boolean.TRUE);
            }
        });

        return toggleAutoConfirmationButton;
    }

}
