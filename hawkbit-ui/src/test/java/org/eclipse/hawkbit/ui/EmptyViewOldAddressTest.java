/**
 * Copyright (c) 2023 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.hawkbit.ui;

import static org.assertj.core.api.Assertions.assertThat;

import org.eclipse.hawkbit.ui.AbstractHawkbitUI.EmptyView;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import io.qameta.allure.Description;
import io.qameta.allure.Feature;
import io.qameta.allure.Story;

/**
 * The pure parts of the empty route's handling of old {@code #!} addresses:
 * which address has a view name, and which can be forwarded as a path inside
 * the UI.
 */
@Feature("Unit Tests - Management UI")
@Story("Old address handling")
class EmptyViewOldAddressTest {

    @ParameterizedTest
    @ValueSource(strings = { "//host", "/x", "/deployment", "%2Fx", "%2fx", "/" })
    @Description("An old address with an empty view name opens the start view")
    void emptyViewNameHasNoViewName(final String oldState) {
        assertThat(EmptyView.hasViewName(oldState)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = { "deployment", "deployment/target=seed-dev-03", "http://x", "../x", "./x", "a b", "a%2Fb",
            "a;b", "%zz" })
    @Description("An old address with a view name is handled as an address of a view (path or not)")
    void viewNameIsRead(final String oldState) {
        assertThat(EmptyView.hasViewName(oldState)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = { "deployment", "deployment/target=seed-dev-03", "spSystemConfig", "rollout/id=5",
            "deployment/target=x:y", "deployment/target=a%20b", "deployment/target=a+b" })
    @Description("A plain old address is forwarded as a path inside the UI")
    void plainAddressIsUiPath(final String oldState) {
        assertThat(EmptyView.isUiPath(oldState)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = { "http://x", "https://host/path", "mailto:x", "../x", "./x", "a/../x", "a/./x", "a/..",
            "deployment//x", "a\\b", "a;b", "deployment/target=a;b", "a%2Fb", "a%2fb", "a%5Cb", "a%2Eb", "a%2eb", "a%25b",
            "a%3Bb", "a%3bb" })
    @Description("An old address that is a scheme, has an empty, . or .. segment, a backslash, a semicolon or an encoded / \\ . % ; is not a path: it shows the error text instead")
    void otherAddressIsNotUiPath(final String oldState) {
        assertThat(EmptyView.isUiPath(oldState)).isFalse();
    }

    @Test
    @Description("Encoded characters are decoded; plus and bad escapes are kept")
    void decodeValues() {
        assertThat(EmptyView.decode("a%20b")).isEqualTo("a b");
        assertThat(EmptyView.decode("caf%C3%A9")).isEqualTo("café");
        assertThat(EmptyView.decode("a%2Fb")).isEqualTo("a/b");
        assertThat(EmptyView.decode("a+b")).isEqualTo("a+b");
        assertThat(EmptyView.decode("%zz")).isEqualTo("%zz");
    }
}
