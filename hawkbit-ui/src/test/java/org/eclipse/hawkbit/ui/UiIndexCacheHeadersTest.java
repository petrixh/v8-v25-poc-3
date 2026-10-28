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

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.vaadin.flow.server.communication.IndexHtmlRequestListener;
import com.vaadin.flow.server.communication.IndexHtmlResponse;
import com.vaadin.flow.server.ServiceInitEvent;
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

        indexHtmlListener().modifyIndexHtmlResponse(indexHtml);

        verify(response).setHeader("Cache-Control", "no-cache");
        verify(response).setHeader("Pragma", "no-cache");
        // Expires: Thu, 01 Jan 1970 00:00:00 GMT
        verify(response).setDateHeader("Expires", 0);
        verifyNoMoreInteractions(response);
    }

    @Test
    @Description("Only the index answer is touched: the listener is not a general response filter")
    void listenerIsRegisteredAsIndexHtmlListenerOnly() {
        final ServiceInitEvent event = mock(ServiceInitEvent.class);
        new MgmtUiConfiguration().noCacheIndexHtmlServiceInitListener().serviceInit(event);
        verify(event).addIndexHtmlRequestListener(org.mockito.ArgumentMatchers.any());
        verifyNoMoreInteractions(event);
    }

    private static IndexHtmlRequestListener indexHtmlListener() {
        final ServiceInitEvent event = mock(ServiceInitEvent.class);
        new MgmtUiConfiguration().noCacheIndexHtmlServiceInitListener().serviceInit(event);
        final ArgumentCaptor<IndexHtmlRequestListener> captor = ArgumentCaptor.forClass(IndexHtmlRequestListener.class);
        verify(event).addIndexHtmlRequestListener(captor.capture());
        assertThat(captor.getValue()).isNotNull();
        return captor.getValue();
    }
}
