/**
 * Copyright (c) 2021 Bosch.IO GmbH and others
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.hawkbit.ui.error;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.eclipse.hawkbit.ui.common.notification.ParallelNotification;
import org.eclipse.hawkbit.ui.error.extractors.UiErrorDetailsExtractor;
import org.eclipse.hawkbit.ui.utils.SPUIStyleDefinitions;
import org.eclipse.hawkbit.ui.utils.UIMessageIdProvider;
import org.eclipse.hawkbit.ui.utils.UINotification;
import org.eclipse.hawkbit.ui.utils.VaadinMessageSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.server.ErrorEvent;
import com.vaadin.flow.server.ErrorHandler;

/**
 * Default handler for Hawkbit UI.
 */
public class HawkbitUIErrorHandler implements ErrorHandler {
    private static final long serialVersionUID = 1L;
    private static final Logger LOG = LoggerFactory.getLogger(HawkbitUIErrorHandler.class);

    private static final String SESSION_ALREADY_INVALIDATED = "Session already invalidated";

    private final VaadinMessageSource i18n;
    private final transient List<UiErrorDetailsExtractor> uiErrorDetailsExtractors;

    /**
     * Constructor for HawkbitUIErrorHandler.
     *
     * @param i18n
     *            Message source used for localization
     * @param uiErrorDetailsExtractors
     *            ui error details extractors
     */
    public HawkbitUIErrorHandler(final VaadinMessageSource i18n,
            final List<UiErrorDetailsExtractor> uiErrorDetailsExtractors) {
        this.i18n = i18n;
        this.uiErrorDetailsExtractors = uiErrorDetailsExtractors;
    }

    @Override
    public void error(final ErrorEvent event) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Ui error received. Trying to extract details...", event.getThrowable());
        }

        if (isSessionAlreadyInvalidated(event.getThrowable())) {
            // A sign out invalidated the session while this request (a heartbeat, a push request) was handled, a race
            // in Flow's session lookup that Vaadin 8 has too (2B-26). There is no UI to tell; Flow answers the request
            // itself. Logged with the cause, but without the notification that failed with an NPE here.
            LOG.warn("Session invalidated while the request was handled (sign out), no notification shown",
                    event.getThrowable());
            return;
        }

        final UI currentPage = getPageFrom(event);
        final List<UiErrorDetails> errorDetails = extractErrorDetails(event);

        if (errorDetails.isEmpty()) {
            showGenericErrorNotification(currentPage, event);
            return;
        }

        errorDetails.stream().filter(UiErrorDetails::isPresent)
                .forEach(details -> showSpecificErrorNotification(currentPage, details));
    }

    // The container's IllegalStateException "getAttribute: Session already invalidated" (Tomcat), anywhere in the cause chain
    private static boolean isSessionAlreadyInvalidated(final Throwable throwable) {
        for (Throwable cause = throwable; cause != null; cause = cause.getCause() == cause ? null : cause.getCause()) {
            if (cause instanceof IllegalStateException && cause.getMessage() != null
                    && cause.getMessage().contains(SESSION_ALREADY_INVALIDATED)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Method to find the {@link UI} to show notification on. In Vaadin Flow a
     * {@link com.vaadin.flow.component.page.Page} does not give access to its
     * UI, so the UI takes the place of the Vaadin 8 Page here.
     *
     * @param event
     *            error event
     * @return current {@link UI} for error notification
     */
    protected UI getPageFrom(final ErrorEvent event) {
        // Flow: the component of the event is also the UI itself when the
        // error came from the UI (Vaadin 8: ConnectorErrorEvent of a UI)
        final Optional<UI> errorOriginPage = getErrorOriginPage(event);
        if (errorOriginPage.isPresent()) {
            return errorOriginPage.get();
        }

        return UI.getCurrent();
    }

    private static Optional<UI> getErrorOriginPage(final ErrorEvent event) {
        return event.getComponent().flatMap(Component::getUI);
    }

    private List<UiErrorDetails> extractErrorDetails(final ErrorEvent event) {
        return uiErrorDetailsExtractors.stream()
                .map(extractor -> extractor.extractErrorDetailsFrom(event.getThrowable())).flatMap(Collection::stream)
                .collect(Collectors.toList());
    }

    private void showGenericErrorNotification(final UI page, final ErrorEvent event) {
        LOG.error("Unexpected Ui error occured", event.getThrowable());

        final Notification notification = buildErrorNotification(i18n.getMessage(UIMessageIdProvider.CAPTION_ERROR),
                i18n.getMessage(UIMessageIdProvider.MESSAGE_ERROR));
        showErrorNotification(page, notification);
    }

    private void showSpecificErrorNotification(final UI page, final UiErrorDetails details) {
        final Notification notification = buildErrorNotification(details.getCaption(), details.getDescription());
        showErrorNotification(page, notification);
    }

    /**
     * Method to build an error notification based on caption and description.
     *
     * @param caption
     *            notification caption
     * @param description
     *            notification description
     * @return a hawkbit error notification message
     */
    protected static ParallelNotification buildErrorNotification(final String caption, final String description) {
        return UINotification.buildNotification(SPUIStyleDefinitions.SP_NOTIFICATION_ERROR_MESSAGE_STYLE, caption,
                description, VaadinIcon.EXCLAMATION_CIRCLE, true);
    }

    /**
     * Method to show notification on the given page.
     *
     * @param page
     *            page to show notification on
     * @param notification
     *            notification to show
     */
    protected void showErrorNotification(final UI page, final Notification notification) {
        page.access(notification::open);
    }
}
