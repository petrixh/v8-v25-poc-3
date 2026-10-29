/**
 * Copyright (c) 2023 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.hawkbit.ui.utils;

import com.vaadin.flow.server.ServiceInitEvent;
import com.vaadin.flow.server.VaadinServiceInitListener;

/**
 * Dates and times in the browser's time zone (2B-01): each UI keeps the
 * browser's offset in the session, or fetches it when the UI was built without
 * the browser details ({@code vaadin.eagerServerLoad}), as Vaadin 8's
 * session-wide {@code WebBrowser} had it before the first view
 * ({@link SPDateTimeUtil#trackBrowserTimeZone}).
 */
public class BrowserTimeZoneServiceInitListener implements VaadinServiceInitListener {

    private static final long serialVersionUID = 1L;

    @Override
    public void serviceInit(final ServiceInitEvent event) {
        event.getSource().addUIInitListener(uiInit -> SPDateTimeUtil.trackBrowserTimeZone(uiInit.getUI()));
    }
}
