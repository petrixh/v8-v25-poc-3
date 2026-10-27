/**
 * Copyright (c) 2015 Bosch Software Innovations GmbH and others
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.hawkbit.ui.components;

import java.util.Collection;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.hawkbit.ui.common.event.EntityModifiedEventPayload;
import org.eclipse.hawkbit.ui.common.event.EventNotificationType;
import org.eclipse.hawkbit.ui.common.event.EventTopics;
import org.eclipse.hawkbit.ui.utils.UIComponentIdProvider;
import org.eclipse.hawkbit.ui.utils.VaadinMessageSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.vaadin.spring.events.EventBus.UIEventBus;

import com.vaadin.flow.component.ClickEvent;
import com.vaadin.flow.component.ModalityMode;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.spring.annotation.SpringComponent;
import com.vaadin.flow.spring.annotation.UIScope;

/**
 * Button which shows all notification in a popup.
 */
@SpringComponent
@UIScope
public class NotificationUnreadButton extends Button {
    private static final long serialVersionUID = 1L;

    private static final String TITLE = "notification.unread.button.title";
    private static final String DESCRIPTION = "notification.unread.button.description";

    private static final String STYLE = "notifications-unread";
    private static final String STYLE_UNREAD_COUNTER = "unread";
    private static final String STYLE_POPUP = "notifications-unread-popup";
    private static final String STYLE_NO_CLOSEBOX = "no-closebox";

    private final transient VaadinMessageSource i18n;
    private final transient UIEventBus eventBus;

    private int unreadNotificationCounter;
    private Dialog notificationsWindow;
    private final transient Map<EventNotificationType, EntityModifiedEventPayload> remotelyOriginatedEventsStore;

    /**
     * Constructor.
     * 
     * @param i18n
     *            i18n
     */
    @Autowired
    NotificationUnreadButton(final VaadinMessageSource i18n, final UIEventBus eventBus) {
        this.i18n = i18n;
        this.eventBus = eventBus;

        this.remotelyOriginatedEventsStore = new ConcurrentHashMap<>();

        setId(UIComponentIdProvider.NOTIFICATION_UNREAD_ID);
        setIcon(VaadinIcon.BELL.create());
        setEnabled(false);
        addThemeVariants(ButtonVariant.LUMO_SMALL);
        addClassName(STYLE);

        createNotificationWindow();
        addClickListener(this::dispatchRemotelyOriginatedEvents);
    }

    private void createNotificationWindow() {
        notificationsWindow = new Dialog();

        notificationsWindow.setId(UIComponentIdProvider.NOTIFICATION_UNREAD_POPUP_ID);
        notificationsWindow.setWidth("300px");
        notificationsWindow.setCloseOnEsc(true);
        notificationsWindow.setResizable(false);
        notificationsWindow.setDraggable(false);
        notificationsWindow.addClassName(STYLE_POPUP);
        notificationsWindow.addClassName(STYLE_NO_CLOSEBOX);

        notificationsWindow.addOpenedChangeListener(event -> {
            if (!event.isOpened()) {
                refreshCaption();
            }
        });
        // Vaadin 8 closed the window when it lost focus; a Flow dialog has no
        // blur event: it closes on a click outside instead
        notificationsWindow.setModality(ModalityMode.MODELESS);
        notificationsWindow.setCloseOnOutsideClick(true);
    }

    private void refreshCaption() {
        setSuffixComponent(null);
        setEnabled(notificationsWindow.isOpened());
        if (unreadNotificationCounter > 0) {
            setVisible(true);
            setEnabled(true);
            // was the HTML caption <div class='unread'>counter</div>
            final Span counter = new Span(String.valueOf(unreadNotificationCounter));
            counter.addClassName(STYLE_UNREAD_COUNTER);
            setSuffixComponent(counter);
        }
        setTooltipText(i18n.getMessage(DESCRIPTION, unreadNotificationCounter));
    }

    private void dispatchRemotelyOriginatedEvents(final ClickEvent<Button> event) {
        if (notificationsWindow.isOpened()) {
            notificationsWindow.close();
            return;
        }

        notificationsWindow.removeAll();
        notificationsWindow.add(buildNotificationsWindowLayout());
        // Vaadin 8: button top (clientY - relativeY) + 40 px; Flow click events
        // have no relative position, so the click position is used
        notificationsWindow.setTop((event.getClientY() + 40) + "px");
        notificationsWindow.open();

        dispatchEntityModifiedEvents();

        clear();
    }

    private VerticalLayout buildNotificationsWindowLayout() {
        final VerticalLayout notificationsLayout = new VerticalLayout();
        notificationsLayout.setPadding(true);
        notificationsLayout.setSpacing(true);

        final Span title = new Span(i18n.getMessage(TITLE));
        title.addClassName("h3");
        title.addClassName("no-margin");

        notificationsLayout.add(title);

        final Span[] eventNotificationLabels = remotelyOriginatedEventsStore.entrySet().stream()
                .map(this::buildEventNotificationLabel).toArray(Span[]::new);

        notificationsLayout.add(eventNotificationLabels);

        return notificationsLayout;
    }

    private Span buildEventNotificationLabel(
            final Entry<EventNotificationType, EntityModifiedEventPayload> remotelyOriginatedEvent) {
        final EventNotificationType notificationType = remotelyOriginatedEvent.getKey();
        final int modifiedEntitiesCount = remotelyOriginatedEvent.getValue().getEntityIds().size();
        String message = "";
        if (modifiedEntitiesCount == 1) {
            message = i18n.getMessage(notificationType.getNotificationMessageKeySing());
        } else {
            message = i18n.getMessage(notificationType.getNotificationMessageKeyPlur(),
                    String.valueOf(modifiedEntitiesCount));
        }
        return new Span(message);
    }

    private void dispatchEntityModifiedEvents() {
        final Collection<EntityModifiedEventPayload> remotelyOriginatedEvents = remotelyOriginatedEventsStore.values();
        eventBus.publish(EventTopics.REMOTE_EVENT_DISPATCHED, UI.getCurrent(), remotelyOriginatedEvents);
        remotelyOriginatedEvents
                .forEach(eventPayload -> eventBus.publish(EventTopics.ENTITY_MODIFIED, UI.getCurrent(), eventPayload));
    }

    private void clear() {
        unreadNotificationCounter = 0;
        remotelyOriginatedEventsStore.clear();
        refreshCaption();
    }

    /**
     * Increments the unread notifications
     *
     * @param notificationType
     *            notification type for message
     * @param eventPayload
     *            EntityModifiedEventPayload
     */
    public void incrementUnreadNotification(final EventNotificationType notificationType,
            final EntityModifiedEventPayload eventPayload) {
        remotelyOriginatedEventsStore.merge(notificationType, eventPayload, (oldEventPayload, newEventPayload) -> {
            // currently we do not support parent aware differed events,
            // thus ignoring parentId of the incoming eventPayload
            oldEventPayload.getEntityIds().addAll(newEventPayload.getEntityIds());

            return oldEventPayload;
        });

        unreadNotificationCounter += eventPayload.getEntityIds().size();
        refreshCaption();
    }
}
