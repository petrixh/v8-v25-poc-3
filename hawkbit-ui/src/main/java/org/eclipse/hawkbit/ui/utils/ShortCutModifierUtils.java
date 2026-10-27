/**
 * Copyright (c) 2015 Bosch Software Innovations GmbH and others
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.hawkbit.ui.utils;

import com.vaadin.flow.component.KeyModifier;
import com.vaadin.flow.server.VaadinSession;
import com.vaadin.flow.server.WebBrowser;

/**
 * On different systems there are different modifier for short cuts. This
 * utility class handles the cross-platform functionality.
 */
public final class ShortCutModifierUtils {

    private ShortCutModifierUtils() {

    }

    /**
     * Returns the ctrl or meta modifier depending on the platform.
     * 
     * @return on mac return
     *         {@link KeyModifier#META} other
     *         platform return
     *         {@link KeyModifier#CONTROL}
     */
    public static KeyModifier getCtrlOrMetaModifier() {
        final WebBrowser webBrowser = VaadinSession.getCurrent().getBrowser();
        if (webBrowser.isMacOSX()) {
            return KeyModifier.META;
        }

        return KeyModifier.CONTROL;
    }
}
