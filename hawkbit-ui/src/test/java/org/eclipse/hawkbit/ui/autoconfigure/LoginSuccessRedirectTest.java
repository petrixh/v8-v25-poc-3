/**
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.hawkbit.ui.autoconfigure;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.eclipse.hawkbit.repository.SystemManagement;
import org.eclipse.hawkbit.security.SystemSecurityContext;
import org.eclipse.hawkbit.im.authentication.TenantAwareAuthenticationDetails;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.test.util.ReflectionTestUtils;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.page.Page;
import com.vaadin.flow.server.VaadinServletRequest;
import com.vaadin.flow.server.VaadinServletResponse;
import com.vaadin.flow.internal.CurrentInstance;

import io.qameta.allure.Description;
import io.qameta.allure.Feature;
import io.qameta.allure.Story;

/**
 * 1B-10: the login success redirect keeps the servlet context path in front of
 * relative targets (as vaadin4spring's redirect strategy did); absolute
 * targets stay as they are.
 */
@Feature("Unit Tests - Management UI")
@Story("Login success redirect")
class LoginSuccessRedirectTest {

    // fields keep the mocks reachable: Flow holds the current instances weakly
    private UI ui;
    private Page page;
    private VaadinServletRequest vaadinRequest;
    private VaadinServletResponse vaadinResponse;
    private Authentication authentication;

    @BeforeEach
    void currentUi() {
        ui = mock(UI.class);
        page = mock(Page.class);
        when(ui.getPage()).thenReturn(page);
        UI.setCurrent(ui);
        authentication = mock(Authentication.class);
        when(authentication.getDetails()).thenReturn(new TenantAwareAuthenticationDetails("DEFAULT", false));
    }

    @AfterEach
    void clear() {
        UI.setCurrent(null);
        CurrentInstance.clearAll();
    }

    @Test
    @Description("A relative target (the default) gets the servlet context path")
    void relativeTargetGetsContextPath() throws Exception {
        signIn("/ctx", null);
        verify(page).setLocation("/ctx/UI/");
    }

    @Test
    @Description("A relative target from the r parameter gets the servlet context path")
    void relativeTargetParameterGetsContextPath() throws Exception {
        signIn("/ctx", "/UI/deployment");
        verify(page).setLocation("/ctx/UI/deployment");
    }

    @Test
    @Description("An absolute target from the r parameter stays as it is")
    void absoluteTargetStaysAsIs() throws Exception {
        signIn("/ctx", "https://example.org/somewhere");
        verify(page).setLocation("https://example.org/somewhere");
    }

    @Test
    @Description("Without a context path the target is unchanged")
    void noContextPathLeavesTargetUnchanged() throws Exception {
        signIn("", null);
        verify(page).setLocation("/UI/");
        signIn("", "/UI/deployment");
        verify(page).setLocation("/UI/deployment");
    }

    private void signIn(final String contextPath, final String target) throws Exception {
        final MockHttpServletRequest request = new MockHttpServletRequest();
        request.setContextPath(contextPath);
        if (target != null) {
            request.setParameter("r", target);
        }
        final MockHttpServletResponse response = new MockHttpServletResponse();
        vaadinRequest = mock(VaadinServletRequest.class);
        when(vaadinRequest.getHttpServletRequest()).thenReturn(request);
        vaadinResponse = mock(VaadinServletResponse.class);
        when(vaadinResponse.getHttpServletResponse()).thenReturn(response);
        CurrentInstance.set(com.vaadin.flow.server.VaadinRequest.class, vaadinRequest);
        CurrentInstance.set(com.vaadin.flow.server.VaadinResponse.class, vaadinResponse);

        final UISecurityConfigurationAdapter adapter = new UISecurityConfigurationAdapter();
        final UISecurityConfigurationAdapter.TenantMetadataSavedRequestAwareVaadinAuthenticationSuccessHandler handler = adapter.new TenantMetadataSavedRequestAwareVaadinAuthenticationSuccessHandler(
                "/UI/");
        handler.setTargetUrlParameter("r");
        ReflectionTestUtils.setField(handler, "systemManagement", mock(SystemManagement.class));
        final SystemSecurityContext systemSecurityContext = mock(SystemSecurityContext.class);
        ReflectionTestUtils.setField(handler, "systemSecurityContext", systemSecurityContext);

        handler.onAuthenticationSuccess(authentication);
    }
}
