/**
 * Copyright (c) 2020 Bosch.IO GmbH and others
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.hawkbit.ui.common.grid.header;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import org.eclipse.hawkbit.ui.SpPermissionChecker;
import org.eclipse.hawkbit.ui.common.builder.LabelBuilder;
import org.eclipse.hawkbit.ui.components.SPUIComponentProvider;
import org.eclipse.hawkbit.ui.decorators.SPUIButtonStyleNoBorder;
import org.eclipse.hawkbit.ui.utils.VaadinMessageSource;
import org.vaadin.spring.events.EventBus.UIEventBus;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.html.Span;

/**
 * Abstract header with breadcrumb links to parent grids.
 */
public abstract class AbstractBreadcrumbGridHeader extends AbstractGridHeader {
    private static final long serialVersionUID = 1L;

    protected final Span headerCaptionDetails;
    private final transient List<BreadcrumbLink> breadcrumbLinks;

    /**
     * Constructor for AbstractBreadcrumbGridHeader.
     *
     * @param i18n
     *            VaadinMessageSource
     * @param permChecker
     *            SpPermissionChecker
     * @param eventBus
     *            UIEventBus
     */
    protected AbstractBreadcrumbGridHeader(final VaadinMessageSource i18n, final SpPermissionChecker permChecker,
            final UIEventBus eventBus) {
        super(i18n, permChecker, eventBus);

        this.headerCaptionDetails = createHeaderCaptionDetails();
        this.breadcrumbLinks = new ArrayList<>();
    }

    private Span createHeaderCaptionDetails() {
        final Span captionDetails = new LabelBuilder().id(getHeaderCaptionDetailsId()).name("").buildCaptionLabel();
        captionDetails.addClassNames("breadcrumbPaddingLeft");

        return captionDetails;
    }

    protected abstract String getHeaderCaptionDetailsId();

    protected void addBreadcrumbLinks(final Collection<BreadcrumbLink> breadcrumbLinks) {
        breadcrumbLinks.forEach(this::addBreadcrumbLink);
    }

    protected void addBreadcrumbLink(final BreadcrumbLink breadcrumbLink) {
        if (breadcrumbLink != null) {
            breadcrumbLinks.add(breadcrumbLink);
        }
    }

    @Override
    protected Component getHeaderCaption() {
        final HorizontalLayout headerCaptionLayout = new HorizontalLayout();
        headerCaptionLayout.setPadding(false);
        headerCaptionLayout.setSpacing(false);

        breadcrumbLinks.stream().filter(Objects::nonNull).forEach(breadcrumbLink -> {
            headerCaptionLayout.add(breadcrumbLink.getComponent());
            headerCaptionLayout.add(new Span(">"));
        });

        headerCaptionLayout.add(headerCaptionDetails);

        return headerCaptionLayout;
    }

    /**
     * Class representing breadcrumb link in header.
     */
    public static class BreadcrumbLink {
        private final Button linkComponent;

        /**
         * Constructor for BreadcrumbLink.
         *
         * @param caption
         *            caption
         * @param description
         *            description
         * @param clickCallback
         *            the callback to execute when the link is clicked
         */
        public BreadcrumbLink(final String caption, final String description, final Runnable clickCallback) {
            this.linkComponent = buildLinkComponent(caption, description, clickCallback);
        }

        private static Button buildLinkComponent(final String caption, final String description,
                final Runnable clickCallback) {
            final Button link = SPUIComponentProvider.getButton(null, "", "", null, false, null,
                    SPUIButtonStyleNoBorder.class);
            link.setClassName("small" + " " + "on-focus-no-border link rollout-caption-links");

            link.setTooltipText(description);
            link.setText(caption);
            link.addClickListener(event -> clickCallback.run());

            return link;
        }

        public Component getComponent() {
            return linkComponent;
        }

        public Consumer<String> getSetCaptionCallback() {
            return linkComponent::setText;
        }
    }
}
