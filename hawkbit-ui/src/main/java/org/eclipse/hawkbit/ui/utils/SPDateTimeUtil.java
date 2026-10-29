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

import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.stream.Collectors;

import org.apache.commons.lang3.time.DurationFormatUtils;
import org.springframework.util.StringUtils;

import com.google.common.collect.Maps;
import com.vaadin.flow.component.ComponentUtil;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.page.ExtendedClientDetails;
import com.vaadin.flow.server.VaadinSession;

/**
 * Common Util to get date/time related information.
 */
public final class SPDateTimeUtil {

    private static final String DURATION_FORMAT = "y','M','d','H','m','s";
    private static final Map<Integer, CalendarI18N> DURATION_I18N = Maps.newHashMapWithExpectedSize(6);
    private static String fixedTimeZoneProperty;
    // the browser's raw offset (ms), kept in the session; the zone a UI formatted dates with before its details came
    static final String BROWSER_RAW_OFFSET_ATTRIBUTE = SPDateTimeUtil.class.getName() + ".browserRawTimezoneOffset";
    static final String RENDERED_ZONE_ID = SPDateTimeUtil.class.getName() + ".renderedZoneId";

    static {
        DURATION_I18N.put(0, CalendarI18N.YEAR);
        DURATION_I18N.put(1, CalendarI18N.MONTH);
        DURATION_I18N.put(2, CalendarI18N.DAY);
        DURATION_I18N.put(3, CalendarI18N.HOUR);
        DURATION_I18N.put(4, CalendarI18N.MINUTE);
        DURATION_I18N.put(5, CalendarI18N.SECOND);
    }

    private SPDateTimeUtil() {

    }

    /**
     * Set fixed UI timezone
     *
     * @param fixedTimeZoneProperty
     *            time zone e.g. Europe/Berlin. If time zone is unknown, it will
     *            default to GMT
     */
    public static void initializeFixedTimeZoneProperty(final String fixedTimeZoneProperty) {
        SPDateTimeUtil.fixedTimeZoneProperty = fixedTimeZoneProperty;
    }

    /**
     * Get browser time zone or fixed time zone if configured
     *
     * @return TimeZone
     */
    public static TimeZone getBrowserTimeZone() {

        if (!StringUtils.isEmpty(fixedTimeZoneProperty)) {
            return TimeZone.getTimeZone(fixedTimeZoneProperty);
        }

        // Vaadin 8 kept the browser details per session and had them before the first view rendered. Flow has
        // them per UI, and with vaadin.eagerServerLoad (the UI is built in the request for the page) only after a
        // round trip: until then the offset an earlier UI of the session received is used (trackBrowserTimeZone)
        final UI ui = UI.getCurrent();
        if (ui != null) {
            final ExtendedClientDetails details = ui.getPage().getExtendedClientDetails();
            if (isReceived(details)) {
                return getTimeZoneForRawOffset(details.getRawTimezoneOffset());
            }
        }
        final VaadinSession session = ui != null ? ui.getSession() : VaadinSession.getCurrent();
        final TimeZone tz = getTimeZoneForRawOffset(
                session != null ? (Integer) session.getAttribute(BROWSER_RAW_OFFSET_ATTRIBUTE) : null);
        if (ui != null && ComponentUtil.getData(ui, RENDERED_ZONE_ID) == null) {
            ComponentUtil.setData(ui, RENDERED_ZONE_ID, tz.getID());
        }
        return tz;
    }

    /**
     * The zone Vaadin 8 picked for the browser's raw (non-DST) offset: the last
     * zone id with that raw offset; the JVM default zone if the offset is not
     * known.
     *
     * @param rawOffset
     *            raw offset in milliseconds, or {@code null} if not known
     * @return TimeZone
     */
    static TimeZone getTimeZoneForRawOffset(final Integer rawOffset) {
        TimeZone tz = TimeZone.getDefault();
        if (rawOffset == null) {
            return tz;
        }
        for (final String string : TimeZone.getAvailableIDs(rawOffset)) {
            final TimeZone t = TimeZone.getTimeZone(string);
            if (t.getRawOffset() == rawOffset) {
                tz = t;
            }
        }
        return tz;
    }

    /**
     * Keeps the browser's raw time zone offset in the session, for the UIs
     * that render before their own browser details arrive, and fetches the
     * details if this UI has none yet. If this UI formatted a date before they
     * arrived, in another zone than theirs (the first UI of a session that did
     * not start at the login view), the page is loaded again, so no date stays
     * in the server's zone.
     *
     * @param ui
     *            a UI being initialised
     */
    public static void trackBrowserTimeZone(final UI ui) {
        final ExtendedClientDetails details = ui.getPage().getExtendedClientDetails();
        if (isReceived(details)) {
            ui.getSession().setAttribute(BROWSER_RAW_OFFSET_ATTRIBUTE, details.getRawTimezoneOffset());
            return;
        }
        details.refresh(received -> {
            ui.getSession().setAttribute(BROWSER_RAW_OFFSET_ATTRIBUTE, received.getRawTimezoneOffset());
            final Object renderedZoneId = ComponentUtil.getData(ui, RENDERED_ZONE_ID);
            if (renderedZoneId != null
                    && !renderedZoneId.equals(getTimeZoneForRawOffset(received.getRawTimezoneOffset()).getID())) {
                ui.getPage().reload();
            }
        });
    }

    // Flow's own check (Page.retrieveExtendedClientDetails): a placeholder has
    // no screen width
    private static boolean isReceived(final ExtendedClientDetails details) {
        return details.getScreenWidth() != -1;
    }

    /**
     * Get time zone id .ZoneId.SHORT_IDS used get id if time zone is
     * abbreviated like 'IST'.
     *
     * @param tz
     *         TimeZone
     * @return ZoneId
     */
    public static ZoneId getTimeZoneId(final TimeZone tz) {
        return ZoneId.of(tz.getID(), ZoneId.SHORT_IDS);
    }

    /**
     * Get formatted date with browser time zone.
     *
     * @param lastQueryDate
     *          Last query date
     * @return String formatted date or {@code null} when the provided {@code lastQueryDate} was {@code null}
     */
    public static String getFormattedDate(final Long lastQueryDate) {
        return getFormattedDate(lastQueryDate, SPUIDefinitions.LAST_QUERY_DATE_FORMAT);
    }

    /**
     * Get formatted date with browser time zone.
     *
     * @param lastQueryDate
     *          Last query date
     * @param datePattern
     *            pattern how to format the date (cp. {@code SimpleDateFormat})
     * @return String formatted date or {@code null} when the provided {@code lastQueryDate} was {@code null}
     */
    public static String getFormattedDate(final Long lastQueryDate, final String datePattern) {
        if (lastQueryDate != null) {
            final SimpleDateFormat format = new SimpleDateFormat(datePattern);
            format.setTimeZone(getBrowserTimeZone());
            return format.format(new Date(lastQueryDate));
        }
        return null;
    }

    /**
     * Creates a formatted string of a duration in format '1 year 2 months 3
     * days 4 hours 5 minutes 6 seconds' zero values will be ignored in the
     * formatted string.
     *
     * @param startMillis
     *            the start milliseconds of the duration
     * @param endMillis
     *            the end milliseconds of the duration
     * @param i18N
     *            the i18n service to determine the correct string for e.g.
     *            'year'
     * @return a formatted string for duration label
     */
    public static String getDurationFormattedString(final long startMillis, final long endMillis,
            final VaadinMessageSource i18N) {
        final String formatDuration = DurationFormatUtils.formatPeriod(startMillis, endMillis, DURATION_FORMAT, false,
                getBrowserTimeZone());

        final StringBuilder formattedDuration = new StringBuilder();
        final String[] split = formatDuration.split(",");

        for (int index = 0; index < split.length; index++) {
            if (index != 0 && formattedDuration.length() > 0) {
                formattedDuration.append(' ');
            }
            final int value = Integer.parseInt(split[index]);
            if (value != 0) {
                final String suffix = (value == 1) ? i18N.getMessage(DURATION_I18N.get(index).getSingle())
                        : i18N.getMessage(DURATION_I18N.get(index).getPlural());
                formattedDuration.append(value).append(' ').append(suffix);
            }

        }
        return formattedDuration.toString();

    }

    /**
     * Get list of all time zone offsets supported.
     */
    public static List<String> getAllTimeZoneOffsetIds() {
        return ZoneId.getAvailableZoneIds().stream()
                .map(id -> ZonedDateTime.now(ZoneId.of(id)).getOffset().getId().replace("Z", "+00:00")).distinct()
                .sorted().collect(Collectors.toList());
    }

    /**
     * Get time zone of the browser client to be used as default.
     */
    public static String getClientTimeZoneOffsetId() {
        return getCurrentZonedDateTime().getOffset().getId().replace("Z", "+00:00");
    }

    private static ZonedDateTime getCurrentZonedDateTime() {
        return ZonedDateTime.now(getBrowserTimeZoneId());
    }

    private static ZoneId getBrowserTimeZoneId() {
        return getTimeZoneId(getBrowserTimeZone());
    }

    /**
     * Gets the two weeks date and time in milliseconds
     *
     * @return Two weeks from current date and time in epoc milliseconds
     */
    public static Long twoWeeksFromNowEpochMilli() {
        return getCurrentZonedDateTime().plusWeeks(2).toInstant().toEpochMilli();
    }

    /**
     * Gets the half and hour time in milliseconds
     *
     * @return Half an hour from current date and time in epoc milliseconds
     */
    public static Long halfAnHourFromNowEpochMilli() {
        return getCurrentZonedDateTime().plusMinutes(30).toInstant().toEpochMilli();
    }

    /**
     * Convert local date and time in epoch milliseconds
     *
     * @param localDateTime
     *          Date time
     *
     * @return local date time format
     */
    public static Long localDateTimeToEpochMilli(final LocalDateTime localDateTime) {
        return localDateTime.atZone(getBrowserTimeZoneId()).toInstant().toEpochMilli();
    }

    /**
     * Convert epoch milliseconds in local date and time
     *
     * @param epochMilli
     *          Time in epoch milliseconds
     *
     * @return Epoch milliseconds format
     */
    public static LocalDateTime epochMilliToLocalDateTime(final Long epochMilli) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMilli), getBrowserTimeZoneId());
    }

    /**
     * Enum to get the i18n key for single or plural calendar labels.
     *
     *
     *
     *
     */
    private enum CalendarI18N {
        /**
         *
         */
        YEAR("calendar.year", "calendar.years"),
        /**
         *
         */
        MONTH("calendar.month", "calendar.months"),
        /**
         *
         */
        DAY("calendar.days", "calendar.days"),
        /**
         *
         */
        HOUR("calendar.hour", "calendar.hours"),
        /**
         *
         */
        MINUTE("calendar.minute", "calendar.minutes"),
        /**
         *
         */
        SECOND("calendar.second", "calendar.seconds");

        private final String single;
        private final String plural;

        /**
         *
         */
        CalendarI18N(final String single, final String plural) {
            this.single = single;
            this.plural = plural;
        }

        /**
         * @return the single
         */
        public String getSingle() {
            return single;
        }

        /**
         * @return the plural
         */
        public String getPlural() {
            return plural;
        }
    }
}
