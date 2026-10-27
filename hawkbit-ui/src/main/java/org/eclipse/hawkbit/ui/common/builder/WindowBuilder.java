/**
 * Copyright (c) 2015 Bosch Software Innovations GmbH and others
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.hawkbit.ui.common.builder;

import org.eclipse.hawkbit.ui.common.CommonDialogWindow;
import org.eclipse.hawkbit.ui.common.CommonDialogWindow.ConfirmStyle;
import org.eclipse.hawkbit.ui.common.CommonDialogWindow.SaveDialogCloseListener;
import org.eclipse.hawkbit.ui.decorators.SPUIButtonDecorator;
import org.eclipse.hawkbit.ui.decorators.SPUIButtonStyleNoBorderWithIcon;
import org.eclipse.hawkbit.ui.utils.SPUIDefinitions;
import org.eclipse.hawkbit.ui.utils.SPUIStyleDefinitions;
import org.eclipse.hawkbit.ui.utils.VaadinMessageSource;

import com.vaadin.flow.component.ModalityMode;
import com.vaadin.flow.component.ClickEvent;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.ComponentEventListener;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dialog.Dialog;

/**
 * Builder for Window.
 */
public class WindowBuilder {

    private String caption;
    private Component content;
    private ComponentEventListener<ClickEvent<Button>> cancelButtonClickListener;
    private String helpLink;
    private VaadinMessageSource i18n;
    private final String type;
    private String id;
    private boolean showMandatoryExplanation = true;
    private ConfirmStyle confirmStyle = ConfirmStyle.SAVE;
    private Class<? extends SPUIButtonDecorator> buttonDecorator = SPUIButtonStyleNoBorderWithIcon.class;

    private SaveDialogCloseListener saveDialogCloseListener;

    /**
     * Constructor.
     * 
     * @param type
     *            window type
     */
    public WindowBuilder(final String type) {
        this.type = type;
    }

    /**
     * Set the SaveDialogCloseListener.
     * 
     * @param saveDialogCloseListener
     *            the saveDialogCloseListener
     * @return the window builder
     */
    public WindowBuilder saveDialogCloseListener(final SaveDialogCloseListener saveDialogCloseListener) {
        this.saveDialogCloseListener = saveDialogCloseListener;
        return this;
    }

    /**
     * Set the caption.
     * 
     * @param caption
     *            the caption
     * @return the window builder
     */
    public WindowBuilder caption(final String caption) {
        this.caption = caption;
        return this;
    }

    /**
     * Set the content.
     * 
     * @param content
     *            the content
     * @return the window builder
     */
    public WindowBuilder content(final Component content) {
        this.content = content;
        return this;
    }

    /**
     * Set the cancelButtonClickListener.
     * 
     * @param cancelButtonClickListener
     *            the cancelButtonClickListener
     * @return the window builder
     */
    public WindowBuilder cancelButtonClickListener(
            final ComponentEventListener<ClickEvent<Button>> cancelButtonClickListener) {
        this.cancelButtonClickListener = cancelButtonClickListener;
        return this;
    }

    /**
     * Set the helpLink.
     * 
     * @param helpLink
     *            the helpLink
     * @return the window builder
     */
    public WindowBuilder helpLink(final String helpLink) {
        this.helpLink = helpLink;
        return this;
    }

    /**
     * Set the i18n.
     * 
     * @param i18n
     *            the i18n
     * @return the window builder
     */
    public WindowBuilder i18n(final VaadinMessageSource i18n) {
        this.i18n = i18n;
        return this;
    }

    /**
     * Set the id.
     * 
     * @param id
     *            the id to set * @return the window builder
     * @return the window builder
     */
    public WindowBuilder id(final String id) {
        this.id = id;
        return this;
    }

    /**
     * Hide the line that explains the mandatory decorator
     * 
     * @return the window builder
     */
    public WindowBuilder hideMandatoryExplanation() {
        showMandatoryExplanation = false;
        return this;
    }

    /**
     * Set buttonDecorator.
     * 
     * @param buttonDecorator
     *            the to style the confirm and cancel buttons
     * @return the window builder
     */
    public WindowBuilder buttonDecorator(final Class<? extends SPUIButtonDecorator> buttonDecorator) {
        this.buttonDecorator = buttonDecorator;
        return this;
    }

    /**
     * Set buttonDecorator.
     * 
     * @param confirmStyle
     *            what kind of button is used
     * @return the window builder
     */
    public WindowBuilder confirmStyle(final ConfirmStyle confirmStyle) {
        this.confirmStyle = confirmStyle;
        return this;
    }

    /**
     * Build the common dialog window.
     *
     * @return the window.
     */
    public CommonDialogWindow buildCommonDialogWindow() {
        final CommonDialogWindow window = new CommonDialogWindow(caption, content, helpLink, saveDialogCloseListener,
                cancelButtonClickListener, confirmStyle, buttonDecorator, i18n);
        if (!showMandatoryExplanation) {
            window.hideMandatoryExplanation();
        }
        decorateWindow(window);
        return window;

    }

    // Vaadin 8 setClosable(true|false): the window's close (X) button and
    // Esc; Flow dialogs have no close button, Esc stays as set by the dialog
    private void decorateWindow(final Dialog window) {
        if (id != null) {
            window.setId(id);
        }

        if (SPUIDefinitions.CONFIRMATION_WINDOW.equals(type)) {
            window.setDraggable(false);
            window.addClassNames(SPUIStyleDefinitions.CONFIRMATION_WINDOW_CAPTION);

        } else if (SPUIDefinitions.CREATE_UPDATE_WINDOW.equals(type)) {
            window.setDraggable(true);
        }
    }

    /**
     * Build window based on type.
     *
     * @return Window
     */
    public Dialog buildWindow() {
        final Dialog window = new Dialog(caption);
        window.add(content);
        window.setSizeUndefined();
        window.setModality(ModalityMode.STRICT);
        window.setResizable(false);
        // Vaadin 8 modal windows did not close on a click outside
        window.setCloseOnOutsideClick(false);

        decorateWindow(window);

        if (SPUIDefinitions.CREATE_UPDATE_WINDOW.equals(type)) {
            // Vaadin 8: setClosable(false), no close button and no Esc
            window.setCloseOnEsc(false);
        }

        return window;
    }
}
