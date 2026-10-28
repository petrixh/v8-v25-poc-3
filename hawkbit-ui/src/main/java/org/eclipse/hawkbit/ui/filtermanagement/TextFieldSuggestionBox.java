/**
 * Copyright (c) 2015 Bosch Software Innovations GmbH and others
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.hawkbit.ui.filtermanagement;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.eclipse.hawkbit.repository.rsql.RsqlValidationOracle;
import org.eclipse.hawkbit.repository.rsql.SuggestionContext;
import org.eclipse.hawkbit.repository.rsql.ValidationOracleContext;

import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.listbox.ListBox;
import com.vaadin.flow.component.popover.Popover;
import com.vaadin.flow.component.popover.PopoverPosition;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.dom.DomEvent;

import tools.jackson.databind.JsonNode;

/**
 * Extension for the AutoCompleteTexfield.
 * <p>
 * Vaadin 8 used a GWT extension (client connector, RPC and a pop-up menu) for
 * this (A6). On Flow it is a server-side helper with the same behaviour: on
 * every key-up in the field the server is asked for suggestions for the token
 * at the cursor; a pop-up under the field lists them; Up/Down move the
 * selection, Enter or a click inserts the selected suggestion in place of the
 * token, Esc closes the pop-up; after an insert the server is asked again.
 */
public class TextFieldSuggestionBox implements Serializable {

    private static final long serialVersionUID = 1L;

    private static final String KEY_DOWN = "ArrowDown";
    private static final String KEY_UP = "ArrowUp";
    private static final String KEY_ESCAPE = "Escape";
    private static final String KEY_ENTER = "Enter";

    private static final String EVENT_KEY = "event.key";
    private static final String EVENT_TEXT = "event.target.value";
    private static final String EVENT_CURSOR = "event.target.selectionStart";

    private final transient RsqlValidationOracle rsqlValidationOracle;

    private final ListBox<Suggestion> select;
    private final Popover panel;

    // incremented every time the list is rebuilt or a suggestion is inserted:
    // an insert event for an item of an older list is stale and ignored (a click
    // can arrive as an item click and as a value change; only one may insert)
    private long generation;

    private TextField textFieldWidget;

    /**
     * Constructor.
     * 
     * @param rsqlValidationOracle
     *            the suggestion oracle where to retrieve the suggestions from
     */
    public TextFieldSuggestionBox(final RsqlValidationOracle rsqlValidationOracle) {
        this.rsqlValidationOracle = rsqlValidationOracle;

        this.select = new ListBox<>();
        this.select.setItemLabelGenerator(Suggestion::getText);
        // every item is a text span that inserts its suggestion on a click, also
        // when the item is the one already highlighted (the old GWT menu inserted
        // on any click); plain text, not HTML
        this.select.setRenderer(new ComponentRenderer<>(suggestion -> {
            final Span item = new Span(suggestion.getText());
            item.getStyle().set("display", "block");
            item.addClickListener(event -> insertSuggestion(suggestion));
            return item;
        }));
        this.select.addClassName("autocomplete");
        // a click on the padding around the text of an item reaches the list as a
        // selection change from the client; the programmatic selection by the
        // arrow keys does not come from the client and does not insert
        this.select.addValueChangeListener(event -> {
            if (event.isFromClient() && event.getValue() != null) {
                insertSuggestion(event.getValue());
            }
        });

        this.panel = new Popover(select);
        this.panel.addClassName("suggestion-popup");
        this.panel.setPosition(PopoverPosition.BOTTOM_START);
        this.panel.setOpenOnClick(false);
        this.panel.setOpenOnFocus(false);
        this.panel.setOpenOnHover(false);
        this.panel.setAutofocus(false);
    }

    /**
     * Add this extension to the text field.
     * 
     * @param target
     *            the text field to attach this extension to
     */
    public void extend(final TextField target) {
        this.textFieldWidget = target;
        panel.setTarget(target);

        // the key-up target is the native input of the text field: its value
        // and cursor position come with the event (standard DOM properties)
        target.getElement().addEventListener("keyup", this::onKeyUp).addEventData(EVENT_KEY)
                .addEventData(EVENT_TEXT).addEventData(EVENT_CURSOR);
    }

    private void onKeyUp(final DomEvent event) {
        if (panel.isOpened()) {
            handlePanelEventDelegation(event.getEventData());
        } else {
            doAskForSuggestion(event.getEventData());
        }
    }

    private void handlePanelEventDelegation(final JsonNode eventData) {
        switch (eventData.path(EVENT_KEY).asString("")) {
        case KEY_DOWN:
            moveSelection(1);
            break;
        case KEY_UP:
            moveSelection(-1);
            break;
        case KEY_ESCAPE:
            panel.close();
            break;
        case KEY_ENTER:
            if (select.getValue() != null) {
                insertSuggestion(select.getValue());
            }
            break;
        default:
            doAskForSuggestion(eventData);
        }
    }

    private void doAskForSuggestion(final JsonNode eventData) {
        final JsonNode textData = eventData.path(EVENT_TEXT);
        final JsonNode cursorData = eventData.path(EVENT_CURSOR);
        final String text = textData.isString() ? textData.asString() : textFieldWidget.getValue();
        final int cursor = cursorData.isNumber() ? cursorData.asInt() : text.length();
        suggest(text, cursor);
    }

    /**
     * Asks the oracle for suggestions for the token at the cursor and shows
     * them.
     * 
     * @param text
     *            the text of the field
     * @param cursor
     *            the cursor position in the text
     */
    public void suggest(final String text, final int cursor) {
        final ValidationOracleContext suggest = rsqlValidationOracle.suggest(text, cursor);
        showSuggestions(suggest.getSuggestionContext());
    }

    private void showSuggestions(final SuggestionContext suggestionContext) {
        generation++;
        final List<Suggestion> suggestions = mapToDto(suggestionContext, generation);
        if (!suggestions.isEmpty()) {
            select.setItems(suggestions);
            panel.open();
            moveSelection(1);
            return;
        }
        select.setItems(Collections.emptyList());
        panel.close();
    }

    // selection moves like the old GWT menu: wraps around at both ends
    private void moveSelection(final int step) {
        final List<Suggestion> items = select.getListDataView().getItems().collect(Collectors.toList());
        if (items.isEmpty()) {
            return;
        }
        final int index = items.indexOf(select.getValue());
        final int next;
        if (index < 0) {
            next = step > 0 ? 0 : items.size() - 1;
        } else {
            next = Math.floorMod(index + step, items.size());
        }
        select.setValue(items.get(next));
    }

    private void insertSuggestion(final Suggestion suggestToken) {
        if (suggestToken.generation != generation) {
            return;
        }
        generation++;
        final StringBuilder builder = new StringBuilder(textFieldWidget.getValue());
        builder.replace(suggestToken.start, suggestToken.end + 1, suggestToken.text);
        textFieldWidget.setValue(builder.toString());
        panel.close();
        textFieldWidget.focus();
        // setting the value puts the browser's cursor at the end of the text
        suggest(builder.toString(), builder.length());
    }

    private static List<Suggestion> mapToDto(final SuggestionContext suggestionContext, final long generation) {
        if (suggestionContext == null || suggestionContext.getSuggestions() == null) {
            return Collections.emptyList();
        }
        return suggestionContext.getSuggestions().stream()
                .filter(suggestion -> suggestion.getTokenImageName() == null
                        || suggestion.getSuggestion().contains(suggestion.getTokenImageName()))
                .map(suggestion -> new Suggestion(suggestion.getSuggestion(), suggestion.getStart(),
                        suggestion.getEnd(), generation))
                .collect(Collectors.toList());
    }

    // serializable copy of a SuggestToken (the repository type is not Serializable)
    private static final class Suggestion implements Serializable {

        private static final long serialVersionUID = 1L;

        private final String text;
        private final int start;
        private final int end;
        private final long generation;

        private Suggestion(final String text, final int start, final int end, final long generation) {
            this.text = text;
            this.start = start;
            this.end = end;
            this.generation = generation;
        }

        private String getText() {
            return text;
        }
    }

}
