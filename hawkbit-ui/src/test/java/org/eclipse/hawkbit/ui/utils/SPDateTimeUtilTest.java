/**
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.hawkbit.ui.utils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;
import java.util.TimeZone;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.vaadin.flow.component.ComponentUtil;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.page.ExtendedClientDetails;
import com.vaadin.flow.component.page.Page;
import com.vaadin.flow.function.SerializableConsumer;
import com.vaadin.flow.server.VaadinSession;

import io.qameta.allure.Description;
import io.qameta.allure.Feature;
import io.qameta.allure.Story;

/**
 * 2B-01: dates in the browser's time zone, picked from its raw offset as
 * Vaadin 8 did, also before the UI has its own browser details
 * ({@code vaadin.eagerServerLoad}).
 */
@Feature("Unit Tests - Management UI")
@Story("Browser time zone")
class SPDateTimeUtilTest {

    private static final int HELSINKI_RAW = 2 * 3600 * 1000;
    private static final int NEW_YORK_RAW = -5 * 3600 * 1000;

    private final Map<String, Object> sessionAttributes = new HashMap<>();
    private final VaadinSession session = mock(VaadinSession.class);
    private final Page page = mock(Page.class);
    private final ExtendedClientDetails details = mock(ExtendedClientDetails.class);
    // Vaadin holds the current UI weakly: keep a reference
    private final UI ui = new UI() {
        private static final long serialVersionUID = 1L;

        @Override
        public VaadinSession getSession() {
            return session;
        }

        @Override
        public Page getPage() {
            return page;
        }
    };

    @BeforeEach
    void setUp() {
        when(session.getAttribute(anyString())).thenAnswer(i -> sessionAttributes.get(i.getArgument(0, String.class)));
        doAnswer(i -> sessionAttributes.put(i.getArgument(0), i.getArgument(1))).when(session)
                .setAttribute(anyString(), any());
        when(page.getExtendedClientDetails()).thenReturn(details);
        SPDateTimeUtil.initializeFixedTimeZoneProperty(null);
        UI.setCurrent(ui);
    }

    @AfterEach
    void tearDown() {
        SPDateTimeUtil.initializeFixedTimeZoneProperty(null);
        UI.setCurrent(null);
    }

    private void detailsReceived(final int rawOffset) {
        when(details.getScreenWidth()).thenReturn(1400);
        when(details.getRawTimezoneOffset()).thenReturn(rawOffset);
    }

    private void detailsNotReceived() {
        // Flow's placeholder: no screen width, raw offset 0
        when(details.getScreenWidth()).thenReturn(-1);
        when(details.getRawTimezoneOffset()).thenReturn(0);
    }

    /** The Vaadin 8 loop, unchanged, as the reference. */
    private static TimeZone vaadin8Pick(final int rawOffset) {
        TimeZone tz = TimeZone.getDefault();
        for (final String id : TimeZone.getAvailableIDs(rawOffset)) {
            final TimeZone t = TimeZone.getTimeZone(id);
            if (t.getRawOffset() == rawOffset) {
                tz = t;
            }
        }
        return tz;
    }

    @Test
    @Description("The zone for a raw offset is the one the Vaadin 8 code picked; an unknown offset gives the JVM zone")
    void zoneForRawOffsetAsVaadin8() {
        assertThat(SPDateTimeUtil.getTimeZoneForRawOffset(HELSINKI_RAW).getID())
                .isEqualTo(vaadin8Pick(HELSINKI_RAW).getID());
        assertThat(SPDateTimeUtil.getTimeZoneForRawOffset(NEW_YORK_RAW).getID())
                .isEqualTo(vaadin8Pick(NEW_YORK_RAW).getID());
        assertThat(SPDateTimeUtil.getTimeZoneForRawOffset(HELSINKI_RAW).getRawOffset()).isEqualTo(HELSINKI_RAW);
        assertThat(SPDateTimeUtil.getTimeZoneForRawOffset(null)).isEqualTo(TimeZone.getDefault());
    }

    @Test
    @Description("A UI with its browser details uses their offset")
    void detailsOfTheUi() {
        detailsReceived(NEW_YORK_RAW);
        sessionAttributes.put(SPDateTimeUtil.BROWSER_RAW_OFFSET_ATTRIBUTE, HELSINKI_RAW);

        assertThat(SPDateTimeUtil.getBrowserTimeZone().getRawOffset()).isEqualTo(NEW_YORK_RAW);
        assertThat(ComponentUtil.getData(ui, SPDateTimeUtil.RENDERED_ZONE_ID)).isNull();
    }

    @Test
    @Description("Before the UI has its details (first render), the offset an earlier UI of the session got is used, not UTC")
    void sessionOffsetBeforeDetails() {
        detailsNotReceived();
        sessionAttributes.put(SPDateTimeUtil.BROWSER_RAW_OFFSET_ATTRIBUTE, HELSINKI_RAW);

        final TimeZone tz = SPDateTimeUtil.getBrowserTimeZone();
        assertThat(tz.getRawOffset()).isEqualTo(HELSINKI_RAW);
        assertThat(ComponentUtil.getData(ui, SPDateTimeUtil.RENDERED_ZONE_ID)).isEqualTo(tz.getID());
    }

    @Test
    @Description("The fixed time zone property wins over the browser's zone")
    void fixedTimeZone() {
        detailsReceived(NEW_YORK_RAW);
        SPDateTimeUtil.initializeFixedTimeZoneProperty("Asia/Tokyo");

        assertThat(SPDateTimeUtil.getBrowserTimeZone().getID()).isEqualTo("Asia/Tokyo");
    }

    @Test
    @Description("A UI built with its details keeps their offset in the session and fetches nothing")
    void trackReceived() {
        detailsReceived(NEW_YORK_RAW);

        SPDateTimeUtil.trackBrowserTimeZone(ui);

        assertThat(sessionAttributes).containsEntry(SPDateTimeUtil.BROWSER_RAW_OFFSET_ATTRIBUTE, NEW_YORK_RAW);
        verify(details, never()).refresh(any());
    }

    @SuppressWarnings("unchecked")
    private SerializableConsumer<ExtendedClientDetails> trackAndCaptureRefresh() {
        SPDateTimeUtil.trackBrowserTimeZone(ui);
        final ArgumentCaptor<SerializableConsumer<ExtendedClientDetails>> callback = ArgumentCaptor
                .forClass(SerializableConsumer.class);
        verify(details).refresh(callback.capture());
        return callback.getValue();
    }

    private ExtendedClientDetails arrived(final int rawOffset) {
        final ExtendedClientDetails received = mock(ExtendedClientDetails.class);
        when(received.getScreenWidth()).thenReturn(1400);
        when(received.getRawTimezoneOffset()).thenReturn(rawOffset);
        return received;
    }

    @Test
    @Description("A UI without details and without dates (login view) fetches them, keeps them, and is not reloaded")
    void trackWithoutDatesShown() {
        detailsNotReceived();
        final SerializableConsumer<ExtendedClientDetails> callback = trackAndCaptureRefresh();

        callback.accept(arrived(HELSINKI_RAW));

        assertThat(sessionAttributes).containsEntry(SPDateTimeUtil.BROWSER_RAW_OFFSET_ATTRIBUTE, HELSINKI_RAW);
        verify(page, never()).reload();
    }

    @Test
    @Description("Dates shown in the session's zone before the details came: no reload when the zone is the same")
    void trackSameZone() {
        detailsNotReceived();
        sessionAttributes.put(SPDateTimeUtil.BROWSER_RAW_OFFSET_ATTRIBUTE, HELSINKI_RAW);
        final SerializableConsumer<ExtendedClientDetails> callback = trackAndCaptureRefresh();
        SPDateTimeUtil.getBrowserTimeZone();

        callback.accept(arrived(HELSINKI_RAW));

        verify(page, never()).reload();
    }

    @Test
    @Description("Dates shown in another zone before the details came (first UI of a session without the login view): reload")
    void trackOtherZoneReloads() {
        detailsNotReceived();
        final SerializableConsumer<ExtendedClientDetails> callback = trackAndCaptureRefresh();
        // formatted with the JVM zone: nothing known yet
        SPDateTimeUtil.getBrowserTimeZone();
        final int other = TimeZone.getDefault().getRawOffset() == HELSINKI_RAW ? NEW_YORK_RAW : HELSINKI_RAW;

        callback.accept(arrived(other));

        assertThat(sessionAttributes).containsEntry(SPDateTimeUtil.BROWSER_RAW_OFFSET_ATTRIBUTE, other);
        verify(page).reload();
    }
}
