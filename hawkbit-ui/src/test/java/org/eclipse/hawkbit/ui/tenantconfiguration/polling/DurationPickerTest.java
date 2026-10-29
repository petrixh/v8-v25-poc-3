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

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.IntStream;

import org.eclipse.hawkbit.ui.utils.VaadinMessageSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticMessageSource;

import com.vaadin.flow.component.AbstractField.ComponentValueChangeEvent;
import com.vaadin.flow.component.ComponentUtil;
import com.vaadin.flow.component.HasValue.ValueChangeEvent;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.TextField;

import io.qameta.allure.Feature;
import io.qameta.allure.Story;

/**
 * The hour/minute/second popup of the System Config duration fields: what it
 * offers, how a choice maps to the field's value (limits, invalid text), and
 * that typing still works as before.
 */
@Feature("Unit Tests - Management UI")
@Story("System Config duration popup")
class DurationPickerTest {

    private static final String INVALID = "Input is not in hh:mm:ss/hhmmss format";

    private DurationField field;
    private DurationPicker picker;
    private final List<ValueChangeEvent<LocalDateTime>> events = new ArrayList<>();

    @BeforeEach
    void setUp() {
        final StaticMessageSource messages = new StaticMessageSource();
        messages.addMessage("configuration.datetime.format.invalid", Locale.getDefault(), INVALID);
        field = new DurationField();
        field.setI18n(new VaadinMessageSource(messages));
        // the System Config range: hawkbit.controller.minPollingTime / maxPollingTime
        field.setMinimumDuration(Duration.ofSeconds(30));
        field.setMaximumDuration(Duration.ofHours(23).plusMinutes(59).plusSeconds(59));
        field.setDuration(Duration.ofMinutes(5));
        picker = field.getPicker();
        field.addValueChangeListener(events::add);
    }

    @Test
    void offersHoursMinutesAndSecondsAsTwoDigits() {
        assertThat(items(picker.getHours())).containsExactlyElementsOf(range(24));
        assertThat(items(picker.getMinutes())).containsExactlyElementsOf(range(60));
        assertThat(items(picker.getSeconds())).containsExactlyElementsOf(range(60));
        assertThat(picker.getHours().getItemLabelGenerator().apply(0)).isEqualTo("00");
        assertThat(picker.getSeconds().getItemLabelGenerator().apply(59)).isEqualTo("59");
    }

    @Test
    void selectsShowTheFieldsValue() {
        assertThat(picker.selectedTime()).isEqualTo(LocalTime.of(0, 5, 0));

        field.setDuration(Duration.ofHours(1).plusMinutes(2).plusSeconds(3));
        picker.showFieldValue();

        assertThat(picker.selectedTime()).isEqualTo(LocalTime.of(1, 2, 3));
    }

    @Test
    void choiceIsWrittenIntoTheFieldAsUserInput() {
        choose(1, 2, 3);

        assertThat(field.getValue().toLocalTime()).isEqualTo(LocalTime.of(1, 2, 3));
        assertThat(field.getTextField().getValue()).isEqualTo("01:02:03");
        assertThat(field.isInvalid()).isFalse();
        assertThat(events).hasSize(1);
        assertThat(events.get(0).isFromClient()).isTrue();
    }

    @Test
    void choiceBelowTheMinimumIsRaisedToTheMinimum() {
        choose(0, 0, 10);

        assertThat(field.getValue().toLocalTime()).isEqualTo(LocalTime.of(0, 0, 30));
        assertThat(field.getTextField().getValue()).isEqualTo("00:00:30");
        assertThat(picker.selectedTime()).isEqualTo(LocalTime.of(0, 0, 30));
    }

    @Test
    void choiceAboveTheMaximumIsLoweredToTheMaximum() {
        field.setMaximumDuration(Duration.ofHours(2));

        choose(5, 30, 0);

        assertThat(field.getValue().toLocalTime()).isEqualTo(LocalTime.of(2, 0, 0));
        assertThat(field.getTextField().getValue()).isEqualTo("02:00:00");
        assertThat(picker.selectedTime()).isEqualTo(LocalTime.of(2, 0, 0));
    }

    @Test
    void highestChoiceIsTheSystemConfigMaximum() {
        choose(23, 59, 59);

        assertThat(field.getValue().toLocalTime()).isEqualTo(LocalTime.of(23, 59, 59));
    }

    @Test
    void choiceReplacesInvalidText() {
        type("25:99");
        assertThat(field.isInvalid()).isTrue();
        assertThat(field.getErrorMessage()).isEqualTo(INVALID);
        assertThat(field.getValue()).isNull();
        // while the text cannot be parsed the selects keep the last value
        picker.showFieldValue();
        assertThat(picker.selectedTime()).isEqualTo(LocalTime.of(0, 5, 0));

        choose(0, 10, 0);

        assertThat(field.isInvalid()).isFalse();
        assertThat(field.getDefaultValidator().apply(field.getValue(), null).isError()).isFalse();
        assertThat(field.getValue().toLocalTime()).isEqualTo(LocalTime.of(0, 10, 0));
        assertThat(field.getTextField().getValue()).isEqualTo("00:10:00");
    }

    @Test
    void typingStillParsesBothFormats() {
        type("00:15:00");
        assertThat(field.getValue().toLocalTime()).isEqualTo(LocalTime.of(0, 15, 0));

        type("1500");
        assertThat(field.getValue().toLocalTime()).isEqualTo(LocalTime.of(0, 15, 0));
        assertThat(field.getTextField().getValue()).isEqualTo("00:15:00");

        type("00:00:05");
        assertThat(field.getTextField().getValue()).isEqualTo("00:00:30");
    }

    @Test
    void popupButtonIsDisabledWithTheField() {
        field.setEnabled(false);
        assertThat(field.getPickerButton().isEnabled()).isFalse();

        field.setEnabled(true);
        assertThat(field.getPickerButton().isEnabled()).isTrue();
    }

    @Test
    void choiceIsIgnoredWhileTheFieldIsDisabled() {
        field.setEnabled(false);

        choose(1, 2, 3);

        assertThat(field.getValue().toLocalTime()).isEqualTo(LocalTime.of(0, 5, 0));
        assertThat(field.getTextField().getValue()).isEqualTo("00:05:00");
        assertThat(events).isEmpty();
        // the selects show the field's value again
        assertThat(picker.selectedTime()).isEqualTo(LocalTime.of(0, 5, 0));

        field.setEnabled(true);
        choose(1, 2, 3);
        assertThat(field.getValue().toLocalTime()).isEqualTo(LocalTime.of(1, 2, 3));
    }

    @Test
    void choiceIsIgnoredWhileTheFieldIsReadOnly() {
        field.setReadOnly(true);

        choose(1, 2, 3);

        assertThat(field.getValue().toLocalTime()).isEqualTo(LocalTime.of(0, 5, 0));
        assertThat(events).isEmpty();
    }

    @Test
    void popupOpensFromThePrefixButtonOnClick() {
        assertThat(field.getTextField().getPrefixComponent()).isSameAs(field.getPickerButton());
        assertThat(picker.getTarget()).isSameAs(field.getPickerButton());
        assertThat(picker.isOpenOnClick()).isTrue();
        assertThat(picker.isOpenOnHover()).isFalse();
        assertThat(picker.isOpenOnFocus()).isFalse();
        assertThat(picker.isCloseOnEsc()).isTrue();
        assertThat(picker.isCloseOnOutsideClick()).isTrue();
    }

    @Test
    void captionNamesThePopupButton() {
        field.setPickerCaption("Polling Time");

        assertThat(field.getPickerButton().getAriaLabel()).contains("Polling Time");
    }

    private void choose(final int hours, final int minutes, final int seconds) {
        picker.getHours().setValue(hours);
        picker.getMinutes().setValue(minutes);
        picker.getSeconds().setValue(seconds);
        picker.pick();
    }

    /** Text typed by the user: the text field's value change from the client. */
    private void type(final String text) {
        final TextField textField = field.getTextField();
        final String old = textField.getValue();
        textField.setValue(text);
        ComponentUtil.fireEvent(textField, new ComponentValueChangeEvent<>(textField, textField, old, true));
    }

    private static List<Integer> items(final Select<Integer> select) {
        return select.getListDataView().getItems().toList();
    }

    private static List<Integer> range(final int count) {
        return IntStream.range(0, count).boxed().toList();
    }
}
