/**
 * Copyright (c) 2020 Bosch.IO GmbH and others
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.hawkbit.ui.distributions.smtype.filter;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.eclipse.hawkbit.repository.SoftwareModuleTypeManagement;
import org.eclipse.hawkbit.repository.model.SoftwareModuleType;
import org.eclipse.hawkbit.repository.model.Type;
import org.eclipse.hawkbit.ui.utils.HawkbitCommonUtil;
import org.eclipse.hawkbit.ui.utils.SPUIDefinitions;
import org.eclipse.hawkbit.ui.utils.UIComponentIdProvider;

import com.vaadin.flow.component.UI;

/**
 * Css style handler for software module type
 * <p>
 * Vaadin 25 (add-on A9): the script that recreates the style element is
 * inline (Flow has no {@code vaadin://} resources), and the rules target the
 * part name that the software module grid sets on the cells of a row, because
 * grid cells are in the grid's shadow DOM.
 */
public class SmTypeCssStylesHandler {
    private final SoftwareModuleTypeManagement softwareModuleTypeManagement;

    /**
     * Constructor for SmTypeCssStylesHandler
     *
     * @param softwareModuleTypeManagement
     *            SoftwareModuleTypeManagement
     */
    public SmTypeCssStylesHandler(final SoftwareModuleTypeManagement softwareModuleTypeManagement) {
        this.softwareModuleTypeManagement = softwareModuleTypeManagement;
    }

    /**
     * Update css styling od software module type
     */
    public void updateSmTypeStyles() {
        final String recreateStylesheetScript = "let stylesheet = document.getElementById($0);"
                + "if (stylesheet) { document.head.removeChild(stylesheet); }"
                + "stylesheet = document.createElement('style'); stylesheet.id = $0;"
                + "stylesheet.textContent = $1; document.head.appendChild(stylesheet);";
        final String styleRules = buildStyleRules(getSmTypeIdWithColor(getAllSmTypes()));

        UI.getCurrent().getPage().executeJs(recreateStylesheetScript, UIComponentIdProvider.SM_TYPE_COLOR_STYLE,
                styleRules);
    }

    private List<SoftwareModuleType> getAllSmTypes() {
        return HawkbitCommonUtil.getEntitiesByPageableProvider(softwareModuleTypeManagement::findAll);
    }

    private static Map<Long, String> getSmTypeIdWithColor(final List<SoftwareModuleType> smTypes) {
        return smTypes.stream().collect(Collectors.toMap(Type::getId,
                type -> Optional.ofNullable(type.getColour()).orElse(SPUIDefinitions.DEFAULT_COLOR)));
    }

    private static String buildStyleRules(final Map<Long, String> typeIdWithColor) {
        return typeIdWithColor.entrySet().stream().map(entry -> {
            final String typeClass = String.join("-", UIComponentIdProvider.SM_TYPE_COLOR_CLASS,
                    String.valueOf(entry.getKey()));
            final String typeColor = entry.getValue();

            // "!important" is needed because we are overriding the theme here
            // (alternatively we could provide more specific selector)
            return String.format(
                    "vaadin-grid::part(%1$s) { background-color:%2$s !important; background-image: none !important; }",
                    typeClass, typeColor);
        }).collect(Collectors.joining(" "));
    }
}
