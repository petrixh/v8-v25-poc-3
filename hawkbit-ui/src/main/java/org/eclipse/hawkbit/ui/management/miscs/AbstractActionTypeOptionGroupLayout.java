/**
 * Copyright (c) 2015 Bosch Software Innovations GmbH and others
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.hawkbit.ui.management.miscs;

import org.eclipse.hawkbit.repository.model.Action.ActionType;
import org.eclipse.hawkbit.ui.utils.UIMessageIdProvider;
import org.eclipse.hawkbit.ui.utils.VaadinMessageSource;

import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.radiobutton.RadioButtonGroup;
import com.vaadin.flow.component.shared.Tooltip;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.function.SerializableFunction;

/**
 * Action type option group abstract layout.
 */
public abstract class AbstractActionTypeOptionGroupLayout extends HorizontalLayout {
    private static final long serialVersionUID = 1L;

    protected final VaadinMessageSource i18n;

    protected RadioButtonGroup<ActionType> actionTypeOptionGroup;

    private final String actionTypeOptionGroupId;

    /**
     * Constructor
     * 
     * @param i18n
     *            VaadinMessageSource
     */
    protected AbstractActionTypeOptionGroupLayout(final VaadinMessageSource i18n,
            final String actionTypeOptionGroupId) {
        this.i18n = i18n;
        this.actionTypeOptionGroupId = actionTypeOptionGroupId;

        init();
    }

    private void init() {
        setSizeUndefined();
        createOptionGroup();
        addOptionGroup();
    }

    private void createOptionGroup() {
        actionTypeOptionGroup = new RadioButtonGroup<>();
        actionTypeOptionGroup.setId(actionTypeOptionGroupId);
        actionTypeOptionGroup.setSizeFull();
        actionTypeOptionGroup.addClassNames("horizontal");

        final SerializableFunction<ActionType, VaadinIcon> itemIconGenerator = item -> {
            switch (item) {
            case FORCED:
                return VaadinIcon.BOLT;
            case SOFT:
                return VaadinIcon.USER_CHECK;
            case TIMEFORCED:
                return VaadinIcon.USER_CLOCK;
            case DOWNLOAD_ONLY:
                return VaadinIcon.DOWNLOAD;
            default:
                return null;
            }
        };

        actionTypeOptionGroup.setItemLabelGenerator(item -> {
            switch (item) {
            case FORCED:
                return i18n.getMessage(UIMessageIdProvider.CAPTION_ACTION_FORCED);
            case SOFT:
                return i18n.getMessage(UIMessageIdProvider.CAPTION_ACTION_SOFT);
            case TIMEFORCED:
                return i18n.getMessage(UIMessageIdProvider.CAPTION_ACTION_TIME_FORCED);
            case DOWNLOAD_ONLY:
                return i18n.getMessage(UIMessageIdProvider.CAPTION_ACTION_DOWNLOAD_ONLY);
            default:
                return null;
            }
        });

        final SerializableFunction<ActionType, String> itemDescriptionGenerator = item -> {
            switch (item) {
            case FORCED:
                return i18n.getMessage(UIMessageIdProvider.TOOLTIP_FORCED_ITEM);
            case SOFT:
                return i18n.getMessage(UIMessageIdProvider.TOOLTIP_SOFT_ITEM);
            case TIMEFORCED:
                return i18n.getMessage(UIMessageIdProvider.TOOLTIP_TIMEFORCED_ITEM);
            case DOWNLOAD_ONLY:
                return i18n.getMessage(UIMessageIdProvider.TOOLTIP_DOWNLOAD_ONLY_ITEM);
            default:
                return null;
            }
        };

        // Flow's RadioButtonGroup has no icon or description generator: each
        // item is rendered as icon + caption with the description as tooltip
        actionTypeOptionGroup.setRenderer(new ComponentRenderer<>(item -> {
            final Span itemLabel = new Span();
            final VaadinIcon icon = itemIconGenerator.apply(item);
            if (icon != null) {
                itemLabel.add(icon.create());
            }
            itemLabel.add(actionTypeOptionGroup.getItemLabelGenerator().apply(item));
            final String description = itemDescriptionGenerator.apply(item);
            if (description != null) {
                Tooltip.forComponent(itemLabel).setText(description);
            }
            return itemLabel;
        }));
    }

    protected abstract void addOptionGroup();

    /**
     * @return Radio button group of action type
     */
    public RadioButtonGroup<ActionType> getActionTypeOptionGroup() {
        return actionTypeOptionGroup;
    }
}
