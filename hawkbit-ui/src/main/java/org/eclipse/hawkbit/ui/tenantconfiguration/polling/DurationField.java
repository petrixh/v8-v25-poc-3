/**
 * Copyright (c) 2015 Bosch Software Innovations GmbH and others
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.hawkbit.ui.tenantconfiguration.polling;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

import jakarta.validation.constraints.NotNull;

import org.eclipse.hawkbit.ui.utils.VaadinMessageSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.vaadin.flow.component.customfield.CustomField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.binder.HasValidator;
import com.vaadin.flow.data.binder.Result;
import com.vaadin.flow.data.binder.ValidationResult;
import com.vaadin.flow.data.binder.Validator;

/**
 * This class represents a Field which is optimized to enter a time duration in
 * form HH:mm:ss (see {@link #DURATION_FORMAT_STIRNG}). The format is optimized
 * for the duration input.
 * <p>
 * Vaadin 25: Flow has no date-time field with a free text format and a server
 * side parse fallback. The field is a {@link CustomField} around a
 * {@link TextField} that parses and formats {@code HH:mm:ss} on the server,
 * with the same fallback and error message; the popup with the time selects
 * is gone.
 */
public class DurationField extends CustomField<LocalDateTime> implements HasValidator<LocalDateTime> {
    private static final long serialVersionUID = 1L;

    private static final Logger LOG = LoggerFactory.getLogger(DurationField.class);

    private static final String CSS_STYLE_NAME = "durationfield";
    private VaadinMessageSource i18n;
    private static final String ADDITIONAL_DURATION_STRING = "HHmmss";
    private static final String DURATION_FORMAT_STIRNG = "HH:mm:ss";

    private static final Duration MAXIMUM_DURATION = Duration.ofHours(23).plusMinutes(59).plusSeconds(59);

    private transient LocalDateTime minimumDuration;
    private transient LocalDateTime maximumDuration;

    private final TextField textField = new TextField();
    private String parseErrorMessage;

    /**
     * Creates a DurationField
     */
    protected DurationField() {
        this.addClassNames(CSS_STYLE_NAME);
        this.addClassNames("tiny");
        this.setWidth("100px");
        textField.setWidth("100%");
        add(textField);

        // Vaadin 8 parsed the text with the date format and the fallback
        // below, and wrote the (sanitized) value back in HH:mm:ss
        textField.addValueChangeListener(event -> {
            if (event.isFromClient()) {
                updateValue();
                if (parseErrorMessage == null) {
                    setPresentationValue(getValue());
                }
            }
        });
    }

    @Override
    protected LocalDateTime generateModelValue() {
        final String text = textField.getValue();
        parseErrorMessage = null;
        if (text == null || text.isEmpty()) {
            return sanitizeValue(null);
        }
        final Result<LocalDateTime> result = handleUnparsableDateString(text);
        if (result.isError()) {
            parseErrorMessage = result.getMessage().orElse("");
            setErrorMessage(parseErrorMessage);
            setInvalid(true);
            return null;
        }
        setInvalid(false);
        return sanitizeValue(result.getOrThrow(IllegalStateException::new));
    }

    @Override
    protected void setPresentationValue(final LocalDateTime value) {
        textField.setValue(value == null ? "" : value.format(DateTimeFormatter.ofPattern(DURATION_FORMAT_STIRNG)));
    }

    /**
     * Vaadin 8 marked the field invalid while the text could not be parsed;
     * the binder sees the same through this default validator.
     */
    @Override
    public Validator<LocalDateTime> getDefaultValidator() {
        return (value, context) -> parseErrorMessage == null ? ValidationResult.ok()
                : ValidationResult.error(parseErrorMessage);
    }

    /**
     * Sets the message source
     *
     * @param i18n
     *            VaadinMessageSource
     */
    public void setI18n(final VaadinMessageSource i18n) {
        this.i18n = i18n;
    }

    /**
     * This method is called to handle a non-empty date string from the client
     * if the client could not parse it as a Date. In the current case two
     * different parsing schemas are tried. If parsing is not possible a
     * ConversionException is thrown which marks the DurationField as invalid.
     * (Vaadin 25: called for every non-empty text, see the class comment.)
     *
     * @return the Result of the specified date
     */
    protected Result<LocalDateTime> handleUnparsableDateString(final String value) {
        try {
            return Result.ok(LocalTime.parse(value, DateTimeFormatter.ofPattern(DURATION_FORMAT_STIRNG))
                    .atDate(LocalDate.of(1, 1, 1)));
        } catch (final DateTimeParseException e) {
            LOG.trace("Parsing date with format {} failed in UI: {}", DURATION_FORMAT_STIRNG, e.getMessage());
            try {
                final String adaptedValue = "000000".substring(Math.min(value.length(), 6)) + value;
                final LocalTime parsedTime = LocalTime.parse(adaptedValue,
                        DateTimeFormatter.ofPattern(ADDITIONAL_DURATION_STRING));
                return Result.ok(parsedTime.atDate(LocalDate.of(1, 1, 1)));
            } catch (final DateTimeParseException ex) {
                LOG.trace("Parsing date with format {} failed in UI: {}", ADDITIONAL_DURATION_STRING, ex.getMessage());
                return Result.error(i18n.getMessage("configuration.datetime.format.invalid"));
            }
        }
    }

    @Override
    public void setValue(final LocalDateTime value) {
        final LocalDateTime sanitizedValue = sanitizeValue(value);
        super.setValue(sanitizedValue);
    }

    /**
     * Sanitize the input date time value
     *
     * @param value
     *            Input date time value
     *
     * @return Validated date time value within range of minimum and maximum
     *         duration
     */
    public LocalDateTime sanitizeValue(final LocalDateTime value) {
        if (value == null && minimumDuration != null) {
            return minimumDuration;
        }
        if (value != null && minimumDuration != null && maximumDuration != null
                && minimumDuration.isBefore(maximumDuration)) {

            if (compareTimeOfDates(value, maximumDuration) > 0) {
                return maximumDuration;
            }

            if (compareTimeOfDates(minimumDuration, value) > 0) {
                return minimumDuration;
            }
        }

        return value;
    }

    /**
     * Because parsing done by base class returns a different date than parsing
     * done by the user or converting duration to a date. But for the
     * DurationField comparison only the time is important. This function helps
     * comparing the time and ignores the values for day, month and year.
     *
     * @param d1
     *            date, which time will compared with the time of d2
     * @param d2
     *            date, which time will compared with the time of d1
     *
     * @return the value 0 if the time represented d1 is equal to the time
     *         represented by d2; a value less than 0 if the time of d1 is
     *         before the time of d2; and a value greater than 0 if the time of
     *         d1 is after the time represented by d2.
     */
    private static int compareTimeOfDates(final LocalDateTime d1, final LocalDateTime d2) {
        return d1.toLocalTime().compareTo(d2.toLocalTime());
    }

    /**
     * Sets the duration value
     *
     * @param duration
     *            duration, only values less then 23:59:59 are excepted
     */

    public void setDuration(@NotNull final Duration duration) {
        if (duration.compareTo(MAXIMUM_DURATION) > 0) {
            throw new IllegalArgumentException("The duaration has to be smaller than 23:59:59.");
        }
        setValue(durationToLocalDateTime(duration));
    }

    /**
     * Sets the minimal allowed duration value as a String
     *
     * @param minimumDuration
     *            minimum Duration, only values smaller 23:59:59 are excepted
     */
    public void setMinimumDuration(@NotNull final Duration minimumDuration) {
        if (minimumDuration.compareTo(MAXIMUM_DURATION) > 0) {
            throw new IllegalArgumentException("The minimum duaration has to be smaller than 23:59:59.");
        }
        this.minimumDuration = durationToLocalDateTime(minimumDuration);
    }

    /**
     * Sets the maximum allowed duration value as a String
     *
     * @param maximumDuration
     *            maximumDuration, only values smaller 23:59:59 are excepted
     */
    public void setMaximumDuration(@NotNull final Duration maximumDuration) {
        if (maximumDuration.compareTo(MAXIMUM_DURATION) > 0) {
            throw new IllegalArgumentException("The maximum duaration has to be smaller than 23:59:59.");
        }
        this.maximumDuration = durationToLocalDateTime(maximumDuration);
    }

    private static LocalDateTime durationToLocalDateTime(final Duration duration) {
        if (duration.compareTo(MAXIMUM_DURATION) > 0) {
            throw new IllegalArgumentException("The duaration has to be smaller than 23:59:59.");
        }
        return LocalTime.ofNanoOfDay(duration.toNanos()).atDate(LocalDate.of(1, 1, 1));
    }
}
