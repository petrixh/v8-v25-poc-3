/**
 * Copyright (c) 2021 Bosch.IO GmbH and others
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.hawkbit.ui.management.dstable;

import java.util.List;
import java.util.function.Consumer;

import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.radiobutton.RadioButtonGroup;
import org.eclipse.hawkbit.repository.model.DistributionSetInvalidation.CancelationType;
import org.eclipse.hawkbit.ui.UiProperties;
import org.eclipse.hawkbit.ui.common.CommonDialogWindow;
import org.eclipse.hawkbit.ui.common.CommonDialogWindow.ConfirmStyle;
import org.eclipse.hawkbit.ui.common.CommonDialogWindow.SaveDialogCloseListener;
import org.eclipse.hawkbit.ui.common.builder.WindowBuilder;
import org.eclipse.hawkbit.ui.common.data.proxies.ProxyDistributionSet;
import org.eclipse.hawkbit.ui.components.SPUIComponentProvider;
import org.eclipse.hawkbit.ui.decorators.SPUIButtonStyleTiny;
import org.eclipse.hawkbit.ui.utils.SPUIDefinitions;
import org.eclipse.hawkbit.ui.utils.SPUIStyleDefinitions;
import org.eclipse.hawkbit.ui.utils.UIComponentIdProvider;
import org.eclipse.hawkbit.ui.utils.UIMessageIdProvider;
import org.eclipse.hawkbit.ui.utils.VaadinMessageSource;

import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.shared.Tooltip;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.dialog.Dialog;

/**
 * Showing the consequences for invalidating distribution set.
 */
public class InvalidateDsConsequencesDialog {

    private final Consumer<Boolean> callback;

    private final VaadinMessageSource i18n;

    private final UiProperties uiProperties;

    private final CommonDialogWindow window;

    private final Checkbox stopRolloutsCheckBox;

    private final RadioButtonGroup<CancelationType> cancelationTypeGroup;

    /**
     * Constructor for {@link InvalidateDsConsequencesDialog}
     *
     * @param allDistributionSetsForInvalidation
     *            {@link List} of {@link ProxyDistributionSet} that are selected for
     *            invalidation
     * @param i18n
     *            {@link VaadinMessageSource}
     * @param callback
     *            callback for dialog result
     */
    public InvalidateDsConsequencesDialog(final List<ProxyDistributionSet> allDistributionSetsForInvalidation,
            final VaadinMessageSource i18n, final UiProperties uiProperties, final Consumer<Boolean> callback) {

        this.i18n = i18n;
        this.uiProperties = uiProperties;
        this.cancelationTypeGroup = createCancelationTypeOptionGroup();
        this.stopRolloutsCheckBox = createStopRolloutsCheckbox();

        final VerticalLayout content = new VerticalLayout();
        content.setSpacing(true);
        content.setPadding(true);

        final Span consequencesLabel = new Span(createConsequencesText(allDistributionSetsForInvalidation));
        consequencesLabel.setWidthFull();
        content.add(consequencesLabel);

        content.add(createCancelationTypeInfo());
        content.add(cancelationTypeGroup);
        content.add(stopRolloutsCheckBox);
        content.add(createConsequencesHint());
        addValueChangeListeners();

        final WindowBuilder windowBuilder = new WindowBuilder(SPUIDefinitions.CREATE_UPDATE_WINDOW)
                .id(UIComponentIdProvider.INVALIDATE_DS_CONSEQUENCES)
                .caption(createCaption(allDistributionSetsForInvalidation)).content(content)
                .cancelButtonClickListener(e -> callback.accept(false))
                .saveDialogCloseListener(getSaveDialogCloseListener()).hideMandatoryExplanation()
                .buttonDecorator(SPUIButtonStyleTiny.class).confirmStyle(ConfirmStyle.NEXT).i18n(i18n);

        this.window = windowBuilder.buildCommonDialogWindow();
        window.setSaveButtonEnabled(true);
        window.addClassNames(SPUIStyleDefinitions.CONFIRMBOX_WINDOW_STYLE);
        this.callback = callback;

    }

    private SaveDialogCloseListener getSaveDialogCloseListener() {
        return new SaveDialogCloseListener() {
            @Override
            public void saveOrUpdate() {
                callback.accept(true);
            }

            @Override
            public boolean canWindowSaveOrUpdate() {
                return true;
            }
        };
    }

    private String createCaption(final List<ProxyDistributionSet> allDistributionSetsForInvalidation) {
        String caption = "";
        if (allDistributionSetsForInvalidation.size() == 1) {
            caption = i18n.getMessage(UIMessageIdProvider.CAPTION_INVALIDATE_DISTRIBUTIONSET_CONSEQUENCES_SINGULAR,
                    allDistributionSetsForInvalidation.get(0).getNameVersion());
        } else {
            caption = i18n.getMessage(UIMessageIdProvider.CAPTION_INVALIDATE_DISTRIBUTIONSET_CONSEQUENCES_PLURAL,
                    allDistributionSetsForInvalidation.size());
        }
        return caption;
    }

    private String createConsequencesText(final List<ProxyDistributionSet> allDistributionSetsForInvalidation) {
        if (allDistributionSetsForInvalidation.size() == 1) {
            return i18n.getMessage(UIMessageIdProvider.MESSAGE_INVALIDATE_DISTRIBUTIONSET_CONSEQUENCES_SINGULAR);
        } else {
            return i18n.getMessage(UIMessageIdProvider.MESSAGE_INVALIDATE_DISTRIBUTIONSET_CONSEQUENCES_PLURAL,
                    allDistributionSetsForInvalidation.size());
        }
    }

    private Checkbox createStopRolloutsCheckbox() {
        final Checkbox checkBox = new Checkbox();
        checkBox.setId(UIComponentIdProvider.INVALIDATE_DS_STOP_ROLLOUTS);
        checkBox.setLabel(i18n.getMessage(UIMessageIdProvider.LABEL_INVALIDATE_DS_STOP_ROLLOUTS));
        return checkBox;
    }

    private HorizontalLayout createCancelationTypeInfo() {
        final HorizontalLayout horizontalLabelInfo = new HorizontalLayout();
        final Span typeLabel = new Span(
                i18n.getMessage(UIMessageIdProvider.LABEL_INVALIDATE_DS_TYPE_OF_CANCELLATION));
        final Anchor linkToInvalidationHelp = SPUIComponentProvider.getHelpLink(i18n,
                uiProperties.getLinks().getDocumentation().getDistributionSetInvalidation());
        horizontalLabelInfo.add(typeLabel);
        horizontalLabelInfo.add(linkToInvalidationHelp);
        return horizontalLabelInfo;
    }

    private RadioButtonGroup<CancelationType> createCancelationTypeOptionGroup() {
        final RadioButtonGroup<CancelationType> cancellationTypeOptions = new RadioButtonGroup<>();
        cancellationTypeOptions.setId(UIComponentIdProvider.INVALIDATE_DS_CANCELATION_TYPE);
        cancellationTypeOptions.addClassNames("horizontal");
        cancellationTypeOptions.setSizeUndefined();

        cancellationTypeOptions.setItems(CancelationType.values());
        cancellationTypeOptions.setItemLabelGenerator(this::getCancelationTypeCaptionMessageId);
        // Flow's RadioButtonGroup has no description generator: each item is
        // rendered as its caption with the description as tooltip
        cancellationTypeOptions.setRenderer(new ComponentRenderer<>(item -> {
            final Span itemLabel = new Span(getCancelationTypeCaptionMessageId(item));
            Tooltip.forComponent(itemLabel).setText(getCancelationTypeToolTipMessageId(item));
            return itemLabel;
        }));

        // default shall be "None"
        cancellationTypeOptions.setValue(CancelationType.NONE);

        return cancellationTypeOptions;
    }

    private Span createConsequencesHint() {
        String hint = i18n.getMessage(UIMessageIdProvider.MESSAGE_INVALIDATE_DISTRIBUTIONSET_UNREPEATABLE_HINT);
        final Span hintLabel = new Span(hint);
        hintLabel.setWidthFull();
        hintLabel.setClassName("tiny");
        return hintLabel;
    }

    /**
     * Returns the user selection stop rollouts
     *
     * @return boolean value of checkbox stop rollouts
     */
    boolean isStopRolloutsSelected() {
        return stopRolloutsCheckBox.getValue();
    }

    CancelationType getCancelationType() {
        return cancelationTypeGroup.getValue();
    }

    private void addValueChangeListeners() {
        cancelationTypeGroup.addValueChangeListener(event -> {
            if (event.getValue() == CancelationType.NONE) {
                stopRolloutsCheckBox.setValue(false);
                stopRolloutsCheckBox.setEnabled(true);
            } else {
                stopRolloutsCheckBox.setValue(true);
                stopRolloutsCheckBox.setEnabled(false);
            }
        });
    }

    /**
     * @return confirmation window
     */
    public Dialog getWindow() {
        return window;
    }

    private String getCancelationTypeCaptionMessageId(CancelationType item) {
        switch (item) {
        case FORCE:
            return i18n.getMessage(UIMessageIdProvider.LABEL_CANCEL_ACTION_FORCE);
        case SOFT:
            return i18n.getMessage(UIMessageIdProvider.LABEL_CANCEL_ACTION_SOFT);
        case NONE:
            return i18n.getMessage(UIMessageIdProvider.LABEL_CANCEL_ACTION_NONE);
        default:
            return null;
        }
    }

    private String getCancelationTypeToolTipMessageId(CancelationType item) {
        switch (item) {
        case FORCE:
            return i18n.getMessage(UIMessageIdProvider.TOOLTIP_DISTRIBUTIONSET_INVALIDATE_FORCED);
        case SOFT:
            return i18n.getMessage(UIMessageIdProvider.TOOLTIP_DISTRIBUTIONSET_INVALIDATE_SOFT);
        case NONE:
            return i18n.getMessage(UIMessageIdProvider.TOOLTIP_DISTRIBUTIONSET_INVALIDATE_NONE);
        default:
            return null;
        }
    }
}
