/**
 * Copyright (c) 2015 Bosch Software Innovations GmbH and others
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.hawkbit.app;

import org.eclipse.hawkbit.autoconfigure.security.EnableHawkbitManagedSecurityConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import com.vaadin.flow.component.dependency.StyleSheet;
import com.vaadin.flow.component.page.AppShellConfigurator;
import com.vaadin.flow.component.page.Push;
import com.vaadin.flow.shared.communication.PushMode;
import com.vaadin.flow.shared.ui.Transport;
import com.vaadin.flow.theme.lumo.Lumo;

/**
 * A {@link SpringBootApplication} annotated class with a main method to start.
 * The minimal configuration for the stand alone hawkBit server.
 *
 */
@SpringBootApplication
@EnableHawkbitManagedSecurityConfiguration
// Vaadin 25: the app shell carries the push settings that Vaadin 8 had on MyUI.
// WEBSOCKET_XHR transport is used instead of WEBSOCKET in order to preserve
// the Spring Security context of the HTTP session.
@Push(value = PushMode.AUTOMATIC, transport = Transport.WEBSOCKET_XHR)
// Vaadin 25: the theme (Vaadin 8 had @Theme("hawkbit") on the UI): Lumo with
// its compact preset (density), then hawkBit's global settings from
// hawkbit-ui (META-INF/resources/VAADIN/hawkbit/hawkbit.css)
@StyleSheet(Lumo.STYLESHEET)
@StyleSheet(Lumo.COMPACT_STYLESHEET)
@StyleSheet("VAADIN/hawkbit/hawkbit.css")
// Exception squid:S1118 - Spring boot standard behavior
@SuppressWarnings({ "squid:S1118" })
public class Start implements AppShellConfigurator {

    /**
     * Main method to start the spring-boot application.
     *
     * @param args
     *            the VM arguments.
     */
    // Exception squid:S2095 - Spring boot standard behavior
    @SuppressWarnings({ "squid:S2095" })
    public static void main(final String[] args) {
        SpringApplication.run(Start.class, args);
    }
}
