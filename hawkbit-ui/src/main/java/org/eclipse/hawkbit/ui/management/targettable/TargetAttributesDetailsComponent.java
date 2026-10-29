/**
 * Copyright (c) 2020 Bosch.IO GmbH and others
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.hawkbit.ui.management.targettable;

import java.util.List;
import java.util.Objects;

import org.eclipse.hawkbit.repository.TargetManagement;
import org.eclipse.hawkbit.ui.common.data.proxies.ProxyKeyValueDetails;
import org.eclipse.hawkbit.ui.common.data.proxies.ProxyTargetAttributesDetails;
import org.eclipse.hawkbit.ui.common.detailslayout.KeyValueDetailsComponent;
import org.eclipse.hawkbit.ui.components.SPUIComponentProvider;
import org.eclipse.hawkbit.ui.decorators.SPUIButtonStyleNoBorder;
import org.eclipse.hawkbit.ui.utils.UIComponentIdProvider;
import org.eclipse.hawkbit.ui.utils.VaadinMessageSource;

import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.customfield.CustomField;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;

/**
 * target attributes detail component
 */
public class TargetAttributesDetailsComponent extends CustomField<ProxyTargetAttributesDetails> {
    private static final long serialVersionUID = 1L;

    private final VaadinMessageSource i18n;
    private final transient TargetManagement targetManagement;

    private final VerticalLayout targetAttributesDetailsLayout;

    /**
     * constructor for TargetAttributesDetailsComponent
     *
     * @param i18n
     *          VaadinMessageSource
     * @param targetManagement
     *          TargetManagement
     */
    public TargetAttributesDetailsComponent(final VaadinMessageSource i18n, final TargetManagement targetManagement) {
        this.i18n = i18n;
        this.targetManagement = targetManagement;

        this.targetAttributesDetailsLayout = new VerticalLayout();
        this.targetAttributesDetailsLayout.setSpacing(true);
        this.targetAttributesDetailsLayout.setPadding(false);
        this.targetAttributesDetailsLayout.setSizeFull();
        // Vaadin 8: initContent()
        add(targetAttributesDetailsLayout);

        setReadOnly(true);
    }

    @Override
    public void setValue(final ProxyTargetAttributesDetails value) {
        // Vaadin 8 compared the new value with the overridden getValue() (a
        // new object each time), so every setValue rendered again; Flow
        // compares with its own buffered value and skips an equal one
        if (Objects.equals(value, super.getValue())) {
            setPresentationValue(value);
        } else {
            super.setValue(value);
        }
    }

    @Override
    public ProxyTargetAttributesDetails getValue() {
        // not needed to return meaningful object here, because it is
        // intended to be read-only
        return new ProxyTargetAttributesDetails();
    }

    @Override
    protected ProxyTargetAttributesDetails generateModelValue() {
        return new ProxyTargetAttributesDetails();
    }

    @Override
    protected void setPresentationValue(final ProxyTargetAttributesDetails targetAttributesDetails) {
        targetAttributesDetailsLayout.removeAll();

        if (targetAttributesDetails == null) {
            return;
        }

        final boolean isRequestAttributes = targetAttributesDetails.isRequestAttributes();
        final List<ProxyKeyValueDetails> targetAttributes = targetAttributesDetails.getTargetAttributes();
        final String controllerId = targetAttributesDetails.getControllerId();

        final Button requestAttributesButton = buildRequestAttributesUpdateButton(controllerId, isRequestAttributes);
        final HorizontalLayout topRow = new HorizontalLayout(requestAttributesButton);
        if (isRequestAttributes) {
            topRow.add(buildAttributesUpdateLabel());
        }
        targetAttributesDetailsLayout.add(topRow);

        final VerticalLayout attributesLayout = buildAttributesLayout(targetAttributes);
        targetAttributesDetailsLayout.add(attributesLayout);
        targetAttributesDetailsLayout.setFlexGrow(1.0F, attributesLayout);

    }

    private VerticalLayout buildAttributesLayout(final List<ProxyKeyValueDetails> targetAttributes) {
        final VerticalLayout attributesLayout = new VerticalLayout();
        attributesLayout.setPadding(false);
        attributesLayout.setSpacing(false);

        final KeyValueDetailsComponent attributes = new KeyValueDetailsComponent();
        attributes.disableSpacing();
        attributes.setValue(targetAttributes);
        attributesLayout.add(attributes);

        return attributesLayout;
    }

    private Span buildAttributesUpdateLabel() {
        final Span updateLabel = new Span(i18n.getMessage("label.target.attributes.update.pending"));
        updateLabel.setClassName("small");

        return updateLabel;
    }

    private Button buildRequestAttributesUpdateButton(final String controllerId, final boolean isRequestAttributes) {
        final Button requestAttributesButton = SPUIComponentProvider.getButton(
                UIComponentIdProvider.TARGET_ATTRIBUTES_UPDATE, "", "", "", false, VaadinIcon.REFRESH,
                SPUIButtonStyleNoBorder.class);

        requestAttributesButton.addClickListener(e -> {
            toggleRequestAttributesUpdateButton(e.getSource(), true);
            targetManagement.requestControllerAttributes(controllerId);
        });

        toggleRequestAttributesUpdateButton(requestAttributesButton, isRequestAttributes);

        return requestAttributesButton;
    }

    private void toggleRequestAttributesUpdateButton(final Button requestAttributesButton,
            final boolean isRequestAttributes) {
        requestAttributesButton
                .setTooltipText(isRequestAttributes ? i18n.getMessage("tooltip.target.attributes.update.requested")
                        : i18n.getMessage("tooltip.target.attributes.update.request"));
        requestAttributesButton.setEnabled(!isRequestAttributes);
    }
}
