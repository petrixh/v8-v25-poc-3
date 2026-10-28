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

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.page.PendingJavaScriptResult;

/**
 * Focus for a component that may not be on screen yet. On a fresh page load (login, a deep link) Flow runs the
 * focus call while the page is still hidden until its styles are loaded, and the browser ignores it. This waits
 * until the component is rendered (at most five seconds), then focuses it. It needs {@code executeJs}, so a
 * Content-Security-Policy without {@code 'unsafe-eval'} loses the focus (nothing else).
 */
public final class FocusOnShow {

    private static final String SCRIPT = "return new Promise(resolve => { let tries = 0; const focusWhenShown = () => {"
            + " if (this.getClientRects().length > 0 || ++tries > 100) { this.focus(); resolve(); }"
            + " else { setTimeout(focusWhenShown, 50); } }; focusWhenShown(); });";

    private FocusOnShow() {
    }

    /**
     * Focuses the component as soon as the browser has rendered it.
     *
     * @param component
     *            the component to focus
     * @return the pending result, completed after the focus call
     */
    public static PendingJavaScriptResult focus(final Component component) {
        return component.getElement().executeJs(SCRIPT);
    }
}
