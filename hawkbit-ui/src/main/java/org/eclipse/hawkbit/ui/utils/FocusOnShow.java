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
 * until the component is rendered (at most five seconds), then focuses it. A disabled component is enabled in the
 * browser for the call only (a disabled Flow component takes no focus; Vaadin 8's disabled Sign in button did):
 * the server's enabled state is not touched and the focus stays.
 */
public final class FocusOnShow {

    private static final String SCRIPT = "const el = this; const wait = (tries) => new Promise(resolve => {"
            + " const check = (n) => { if (el.getClientRects().length > 0 || n > 100) { resolve(); } else { setTimeout(() => check(n + 1), 50); } };"
            + " check(tries); });"
            + " return wait(0).then(async () => {"
            + " const wasDisabled = el.disabled === true;"
            + " if (wasDisabled) { el.disabled = false; if (el.updateComplete) { await el.updateComplete; } }"
            + " el.focus();"
            + " if (wasDisabled) { el.disabled = true; } });";

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
