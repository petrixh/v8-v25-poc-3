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

import org.eclipse.hawkbit.ui.SpPermissionChecker;
import org.eclipse.hawkbit.ui.common.layout.MasterEntityAwareComponent;
import org.eclipse.hawkbit.ui.utils.VaadinMessageSource;
import org.springframework.util.StringUtils;
import org.vaadin.spring.events.EventBus.UIEventBus;

import com.vaadin.ui.Alignment;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.html.Span;

/**
 * Abstract header for master entity aware grids.
 *
 * @param <T>
 *            Generic type
 */
public abstract class AbstractMasterAwareGridHeader<T> extends AbstractGridHeader
        implements MasterEntityAwareComponent<T> {
    private static final long serialVersionUID = 1L;

    private final Span entityDetailsCaption;
    private final Span masterEntityDetailsCaption;

    /**
     * Constructor for AbstractMasterAwareGridHeader
     *
     * @param i18n
     *            VaadinMessageSource
     * @param permChecker
     *            SpPermissionChecker
     * @param eventBus
     *            UIEventBus
     */
    protected AbstractMasterAwareGridHeader(final VaadinMessageSource i18n, final SpPermissionChecker permChecker,
            final UIEventBus eventBus) {
        super(i18n, permChecker, eventBus);

        this.entityDetailsCaption = buildEntityDetailsCaption();
        this.masterEntityDetailsCaption = buildMasterEntityDetailsCaption();
    }

    private Span buildEntityDetailsCaption() {
        final Span caption = new Span(i18n.getMessage(getEntityDetailsCaptionMsgKey()));

        caption.addClassNames("small");
        caption.addClassNames("bold");

        return caption;
    }

    protected abstract String getEntityDetailsCaptionMsgKey();

    private Span buildMasterEntityDetailsCaption() {
        final Span caption = new Span();

        caption.setId(getMasterEntityDetailsCaptionId());
        caption.setWidthFull();
        caption.addClassNames("small");
        caption.addClassNames("bold");
        caption.addClassNames("text-bold");
        caption.addClassNames("text-cut");

        return caption;
    }

    protected abstract String getMasterEntityDetailsCaptionId();

    @Override
    protected Component getHeaderCaption() {
        final HorizontalLayout masterAwareCaptionLayout = new HorizontalLayout();
        masterAwareCaptionLayout.setPadding(false);
        masterAwareCaptionLayout.setSpacing(true);
        masterAwareCaptionLayout.setSizeFull();
        masterAwareCaptionLayout.addClassNames("header-caption");

        masterAwareCaptionLayout.add(entityDetailsCaption);
        masterAwareCaptionLayout.setComponentAlignment(entityDetailsCaption, Alignment.TOP_LEFT);
        masterAwareCaptionLayout.setFlexGrow(0.0F, entityDetailsCaption);

        masterAwareCaptionLayout.add(masterEntityDetailsCaption);
        masterAwareCaptionLayout.setComponentAlignment(masterEntityDetailsCaption, Alignment.TOP_LEFT);
        masterAwareCaptionLayout.setFlexGrow(1.0F, masterEntityDetailsCaption);

        return masterAwareCaptionLayout;
    }

    @Override
    public void masterEntityChanged(final T masterEntity) {
        final String masterEntityName = masterEntity != null ? getMasterEntityName(masterEntity) : "";

        if (StringUtils.hasText(masterEntityName)) {
            entityDetailsCaption.setValue(i18n.getMessage(getEntityDetailsCaptionOfMsgKey()));
            masterEntityDetailsCaption.setValue(masterEntityName);
        } else {
            entityDetailsCaption.setValue(i18n.getMessage(getEntityDetailsCaptionMsgKey()));
            masterEntityDetailsCaption.setValue("");
        }
    }

    protected abstract String getMasterEntityName(final T masterEntity);

    protected abstract String getEntityDetailsCaptionOfMsgKey();
}
