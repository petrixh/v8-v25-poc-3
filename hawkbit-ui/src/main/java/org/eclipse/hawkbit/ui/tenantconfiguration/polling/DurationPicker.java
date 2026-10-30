/**
 * Copyright (c) 2023 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.hawkbit.ui.tenantconfiguration.polling;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.stream.IntStream;

import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.FlexComponent.Alignment;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.popover.Popover;
import com.vaadin.flow.component.popover.PopoverPosition;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.select.SelectVariant;

/**
 * The pop-up of a {@link DurationField}: one select each for hours (00-23),
 * minutes (00-59) and seconds (00-59), separated by ":".
 * <p>
 * Vaadin 25: replaces the time part of the Vaadin 8 date-time field's pop-up
 * (its calendar header and body were hidden by the old theme). As there, a
 * choice in a select is written into the field at once (no OK button), the
 * field limits it to its minimum and maximum, and the selects then show the
 * value the field kept. The pop-up opens on a click on the field's prefix
 * button (or Enter or Space on it) and closes on Esc, a click outside it, or
 * Tab out of the seconds select. The date line the old pop-up showed above
 * the selects (today's date, meaningless for a duration) is left out.
 */
final class DurationPicker extends Popover {
    private static final long serialVersionUID = 1L;

    private static final String SELECT_WIDTH = "4.5em";

    private final DurationField field;
    private final Select<Integer> hours = createSelect(24);
    private final Select<Integer> minutes = createSelect(60);
    private final Select<Integer> seconds = createSelect(60);

    DurationPicker(final DurationField field) {
        this.field = field;
        addClassName("duration-picker");
        setPosition(PopoverPosition.BOTTOM_START);
        setOpenOnClick(true);
        setOpenOnFocus(false);
        setOpenOnHover(false);
        // keyboard use: the pop-up takes the focus when it opens, then the
        // hour select gets it (opened listener below)
        setAutofocus(true);

        final HorizontalLayout times = new HorizontalLayout(hours, new Span(":"), minutes, new Span(":"), seconds);
        times.setSpacing(false);
        times.setPadding(false);
        times.addClassName("duration-picker-times");
        times.setAlignItems(Alignment.CENTER);
        times.getStyle().set("gap", "var(--lumo-space-xs)");
        add(times);

        hours.addValueChangeListener(event -> {
            if (event.isFromClient()) {
                pick();
            }
        });
        minutes.addValueChangeListener(event -> {
            if (event.isFromClient()) {
                pick();
            }
        });
        seconds.addValueChangeListener(event -> {
            if (event.isFromClient()) {
                pick();
            }
        });
        // as the old pop-up: Tab (not Shift+Tab) out of the last select closes
        // it; the focus moves on to the field's text input
        seconds.getElement().addEventListener("keydown", event -> close())
                .setFilter("event.key === 'Tab' && !event.shiftKey");
        addOpenedChangeListener(event -> {
            if (event.isOpened()) {
                showFieldValue();
                hours.focus();
            }
        });
        showFieldValue();
    }

    private static Select<Integer> createSelect(final int count) {
        final Select<Integer> select = new Select<>();
        select.setItems(IntStream.range(0, count).boxed().toList());
        select.setItemLabelGenerator(DurationPicker::twoDigits);
        select.setValue(0);
        select.setWidth(SELECT_WIDTH);
        select.addThemeVariants(SelectVariant.LUMO_SMALL);
        return select;
    }

    static String twoDigits(final Integer value) {
        return String.format("%02d", value);
    }

    /**
     * Writes the time chosen in the selects into the field, as the user's
     * input, then shows the value the field kept (limited to its range).
     */
    void pick() {
        field.pickTime(selectedTime());
        showFieldValue();
    }

    /**
     * @return the time the three selects show
     */
    LocalTime selectedTime() {
        return LocalTime.of(valueOf(hours), valueOf(minutes), valueOf(seconds));
    }

    private static int valueOf(final Select<Integer> select) {
        return select.getValue() == null ? 0 : select.getValue();
    }

    /**
     * Shows the field's value in the selects. While the field holds no value
     * (its text cannot be parsed), the selects keep what they showed.
     */
    void showFieldValue() {
        showValue(field.getValue());
    }

    /**
     * Shows a value in the selects; {@code null} leaves them as they are.
     *
     * @param value
     *            the value the field shows
     */
    void showValue(final LocalDateTime value) {
        if (value != null) {
            hours.setValue(value.getHour());
            minutes.setValue(value.getMinute());
            seconds.setValue(value.getSecond());
        }
    }

    Select<Integer> getHours() {
        return hours;
    }

    Select<Integer> getMinutes() {
        return minutes;
    }

    Select<Integer> getSeconds() {
        return seconds;
    }
}
