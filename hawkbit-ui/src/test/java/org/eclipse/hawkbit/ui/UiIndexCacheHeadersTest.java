/**
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.hawkbit.ui;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.vaadin.flow.server.communication.IndexHtmlRequestListener;
import com.vaadin.flow.server.communication.IndexHtmlResponse;
import com.vaadin.flow.server.ServiceInitEvent;
import com.vaadin.flow.server.VaadinServiceInitListener;
import com.vaadin.flow.server.VaadinResponse;

import io.qameta.allure.Description;
import io.qameta.allure.Feature;
import io.qameta.allure.Story;

/**
 * 1B-46 N4: the UI start page carries the cache headers of the Vaadin 8
 * servlet, so Spring Security's no-store defaults do not show on it.
 */
@Feature("Unit Tests - Management UI")
@Story("UI start page cache headers")
class UiIndexCacheHeadersTest {

    @Test
    @Description("The index page listener sets the Vaadin 8 headers on the UI start page")
    void indexAnswerGetsVaadin8CacheHeaders() {
        final VaadinResponse response = mock(VaadinResponse.class);
        final IndexHtmlResponse indexHtml = mock(IndexHtmlResponse.class);
        when(indexHtml.getVaadinResponse()).thenReturn(response);

        final List<IndexHtmlRequestListener> listeners = indexHtmlListeners();
        assertThat(listeners).as("an index HTML listener of the UI configuration").isNotEmpty();
        listeners.forEach(listener -> listener.modifyIndexHtmlResponse(indexHtml));

        verify(response).setHeader("Cache-Control", "no-cache");
        verify(response).setHeader("Pragma", "no-cache");
        // Expires: Thu, 01 Jan 1970 00:00:00 GMT
        verify(response).setDateHeader("Expires", 0);
        verifyNoMoreInteractions(response);
    }

    @Test
    @Description("Only the index answer is touched: no listener of the configuration acts on other answers")
    void noListenerActsOnOtherAnswers() {
        // the init listeners of the configuration register nothing but index
        // HTML listeners (the system messages provider listener needs a bean
        // and is not called here); redirects, errors and REST answers are not
        // passed through them, so they keep Spring Security's headers
        final ServiceInitEvent event = mock(ServiceInitEvent.class);
        noArgInitListeners().forEach(listener -> listener.serviceInit(event));
        verify(event, atLeastOnce()).addIndexHtmlRequestListener(any());
        verifyNoMoreInteractions(event);
    }

    /** The init listeners of the UI configuration that need no bean. */
    private static List<VaadinServiceInitListener> noArgInitListeners() {
        final MgmtUiConfiguration configuration = new MgmtUiConfiguration();
        final List<VaadinServiceInitListener> listeners = new ArrayList<>();
        for (final Method method : MgmtUiConfiguration.class.getDeclaredMethods()) {
            if (VaadinServiceInitListener.class.equals(method.getReturnType()) && method.getParameterCount() == 0) {
                method.setAccessible(true);
                try {
                    listeners.add((VaadinServiceInitListener) method.invoke(configuration));
                } catch (final ReflectiveOperationException e) {
                    throw new IllegalStateException(e);
                }
            }
        }
        return listeners;
    }

    private static List<IndexHtmlRequestListener> indexHtmlListeners() {
        final ServiceInitEvent event = mock(ServiceInitEvent.class);
        noArgInitListeners().forEach(listener -> listener.serviceInit(event));
        final ArgumentCaptor<IndexHtmlRequestListener> captor = ArgumentCaptor.forClass(IndexHtmlRequestListener.class);
        verify(event, atLeast(0)).addIndexHtmlRequestListener(captor.capture());
        return captor.getAllValues();
    }
}
