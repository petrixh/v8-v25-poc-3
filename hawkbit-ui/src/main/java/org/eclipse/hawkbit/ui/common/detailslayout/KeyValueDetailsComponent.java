/**
 * Copyright (c) 2020 Bosch.IO GmbH and others
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.hawkbit.ui.common.detailslayout;

import java.util.Collections;
import java.util.List;

import org.eclipse.hawkbit.ui.common.data.proxies.ProxyKeyValueDetails;
import org.eclipse.hawkbit.ui.utils.SPUIDefinitions;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.customfield.CustomField;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;

/**
 * key value detail to component layout
 */
public class KeyValueDetailsComponent extends CustomField<List<ProxyKeyValueDetails>> {
    private static final long serialVersionUID = 1L;

    private final VerticalLayout keyValueDetailsLayout;

    /**
     * Constructor for KeyValueDetailsComponent
     */
    public KeyValueDetailsComponent() {
        keyValueDetailsLayout = new VerticalLayout();
        keyValueDetailsLayout.setSpacing(true);
        keyValueDetailsLayout.setPadding(false);
        keyValueDetailsLayout.setWidthFull();

        setReadOnly(true);
    }

    @Override
    public List<ProxyKeyValueDetails> getValue() {
        // not needed to return meaningful key-value pairs, because it is
        // intended to be read-only
        return Collections.emptyList();
    }

    @Override
    protected Component initContent() {
        return keyValueDetailsLayout;
    }

    @Override
    protected void doSetValue(final List<ProxyKeyValueDetails> keyValueDetails) {
        keyValueDetailsLayout.removeAll();

        if (keyValueDetails != null) {
            keyValueDetails.forEach(this::addKeyValueDetail);
        }
    }

    private void addKeyValueDetail(final ProxyKeyValueDetails keyValueDetail) {
        final String id = keyValueDetail.getId();
        final String key = keyValueDetail.getKey();
        final String value = keyValueDetail.getValue();

        final Span keyDetail = buildKeyDetail(key);
        final Span valueDetail = buildValueDetail(id, value);

        final HorizontalLayout keyValueDetailLayout = new HorizontalLayout();
        keyValueDetailLayout.setSpacing(true);
        keyValueDetailLayout.setPadding(false);
        keyValueDetailLayout.setWidth(null);

        keyValueDetailLayout.add(keyDetail);
        keyValueDetailLayout.setFlexGrow(0.0F, keyDetail);

        keyValueDetailLayout.add(valueDetail);
        keyValueDetailLayout.setFlexGrow(1.0F, valueDetail);

        keyValueDetailLayout.setDescription(key.concat(": ") + value);

        keyValueDetailsLayout.add(keyValueDetailLayout);
    }

    private static Span buildKeyDetail(final String key) {
        final Span keyLabel = new Span(key + ":");

        keyLabel.addClassNames(SPUIDefinitions.TEXT_STYLE);
        keyLabel.addClassNames("text-bold");

        return keyLabel;
    }

    private static Span buildValueDetail(final String id, final String value) {
        final Span valueLabel = new Span(value);

        valueLabel.setId(id);
        valueLabel.addClassNames(SPUIDefinitions.TEXT_STYLE);

        return valueLabel;
    }

    /**
     * Disable the spacing in keyValueDetailsLayout
     */
    public void disableSpacing() {
        keyValueDetailsLayout.setSpacing(false);
    }
}
