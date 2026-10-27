/**
 * Copyright (c) 2015 Bosch Software Innovations GmbH and others
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.hawkbit.ui.artifacts.upload;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.hawkbit.ui.artifacts.ArtifactUploadState;
import org.eclipse.hawkbit.ui.artifacts.upload.FileUploadProgress.FileUploadStatus;
import org.eclipse.hawkbit.ui.common.data.proxies.ProxyUploadProgress;
import org.eclipse.hawkbit.ui.common.data.proxies.ProxyUploadProgress.ProgressSatus;
import org.eclipse.hawkbit.ui.components.SPUIComponentProvider;
import org.eclipse.hawkbit.ui.decorators.SPUIButtonStyleNoBorder;
import org.eclipse.hawkbit.ui.utils.SPUIStyleDefinitions;
import org.eclipse.hawkbit.ui.utils.UIComponentIdProvider;
import org.eclipse.hawkbit.ui.utils.UIMessageIdProvider;
import org.eclipse.hawkbit.ui.utils.VaadinMessageSource;
import org.springframework.util.StringUtils;

import com.vaadin.flow.component.ModalityMode;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;

/**
 * Dialog that shows the progress of all uploads.
 */
public class UploadProgressInfoWindow extends Dialog {
    private static final long serialVersionUID = 1L;

    private final VaadinMessageSource i18n;

    private final ArtifactUploadState artifactUploadState;

    private final UploadProgressGrid uploadProgressGrid;
    private final VerticalLayout mainLayout;

    private Span windowCaption;
    private Button closeButton;

    private final List<ProxyUploadProgress> uploads;

    UploadProgressInfoWindow(final VaadinMessageSource i18n, final ArtifactUploadState artifactUploadState) {
        this.i18n = i18n;
        this.artifactUploadState = artifactUploadState;

        setPopupProperties();
        createStatusPopupHeaderComponents();

        mainLayout = new VerticalLayout();
        mainLayout.setSpacing(Boolean.TRUE);
        mainLayout.setSizeUndefined();
        setPopupSizeInMinMode();

        uploads = new ArrayList<>();
        uploadProgressGrid = new UploadProgressGrid(i18n);
        uploadProgressGrid.setItems(uploads);

        mainLayout.add(getCaptionLayout(), uploadProgressGrid);
        mainLayout.setFlexGrow(1.0F, uploadProgressGrid);
        add(mainLayout);
    }

    /**
     * Updates the status of each file uploaded in the grid view
     *
     * @param fileUploadProgress
     *            FileUploadProgress
     */
    public void updateUploadProgressInfoRowObject(final FileUploadProgress fileUploadProgress) {
        final FileUploadId fileUploadId = fileUploadProgress.getFileUploadId();
        final ProxyUploadProgress gridUploadItem = uploads.stream()
                .filter(upload -> upload.getFileUploadId().equals(fileUploadId)).findAny()
                .orElse(new ProxyUploadProgress());

        gridUploadItem.setStatus(getStatusRepresentaion(fileUploadProgress.getFileUploadStatus()));
        gridUploadItem.setReason(getFailureReason(fileUploadId));

        final long bytesRead = fileUploadProgress.getBytesRead();
        final long fileSize = fileUploadProgress.getContentLength();
        if (bytesRead > 0 && fileSize > 0) {
            gridUploadItem.setProgress((double) bytesRead / (double) fileSize);
        }

        if (gridUploadItem.getFileUploadId() == null) {
            gridUploadItem.setFileUploadId(fileUploadId);
            uploads.add(gridUploadItem);
        }

        uploadProgressGrid.getDataProvider().refreshItem(gridUploadItem);
    }

    private static ProgressSatus getStatusRepresentaion(final FileUploadStatus uploadStatus) {
        if (uploadStatus == FileUploadStatus.UPLOAD_FAILED) {
            return ProgressSatus.FAILED;
        } else if (uploadStatus == FileUploadStatus.UPLOAD_SUCCESSFUL) {
            return ProgressSatus.FINISHED;
        } else {
            return ProgressSatus.INPROGRESS;
        }
    }

    /**
     * Returns the failure reason for the provided fileUploadId or an empty
     * string but never <code>null</code>.
     * 
     * @param fileUploadId
     * @return the failure reason or an empty String.
     */
    private String getFailureReason(final FileUploadId fileUploadId) {
        String failureReason = "";
        if (artifactUploadState.getFileUploadProgress(fileUploadId) != null) {
            failureReason = artifactUploadState.getFileUploadProgress(fileUploadId).getFailureReason();
        }
        if (StringUtils.isEmpty(failureReason)) {
            return "";
        }
        return failureReason;
    }

    /**
     * Starts the file upload process and maximize the upload view
     *
     * @param fileUploadProgress
     *            FileUploadProgress
     */
    public void onUploadStarted(final FileUploadProgress fileUploadProgress) {
        updateUploadProgressInfoRowObject(fileUploadProgress);

        if (isWindowNotAlreadyAttached()) {
            maximizeWindow();
        }
    }

    private boolean isWindowNotAlreadyAttached() {
        return !isOpened();
    }

    private void restoreState() {
        uploads.clear();
        for (final FileUploadProgress fileUploadProgress : artifactUploadState
                .getAllFileUploadProgressValuesFromOverallUploadProcessList()) {
            updateUploadProgressInfoRowObject(fileUploadProgress);
        }
    }

    private void setPopupProperties() {
        setId(UIComponentIdProvider.UPLOAD_STATUS_POPUP_ID);
        addClassNames(SPUIStyleDefinitions.UPLOAD_INFO);

        setResizable(false);
        setDraggable(true);
        // Vaadin 8 setClosable(false): only the own close button closes it
        setCloseOnEsc(false);
        setCloseOnOutsideClick(false);
        setModality(ModalityMode.STRICT);
    }

    private HorizontalLayout getCaptionLayout() {
        final HorizontalLayout captionLayout = new HorizontalLayout();
        captionLayout.setSizeFull();
        captionLayout.setHeight("36px");
        captionLayout.add(windowCaption, closeButton);
        captionLayout.setFlexGrow(1.0F, windowCaption);
        captionLayout.addClassNames("v-window-header");
        return captionLayout;
    }

    private void createStatusPopupHeaderComponents() {
        windowCaption = new Span(i18n.getMessage(UIMessageIdProvider.CAPTION_ARTIFACT_UPLOAD_POPUP));
        closeButton = getCloseButton();
    }

    private void openWindow() {
        // Flow dialogs open centred
        this.open();
    }

    protected void maximizeWindow() {
        openWindow();
        restoreState();
        artifactUploadState.setStatusPopupMinimized(false);
    }

    private void minimizeWindow() {
        artifactUploadState.setStatusPopupMinimized(true);
        closeWindow();

        if (artifactUploadState.areAllUploadsFinished()) {
            cleanupStates();
        }
    }

    /**
     * Called for every finished (succeeded or failed) upload.
     */
    public void onUploadFinished() {
        if (artifactUploadState.areAllUploadsFinished() && artifactUploadState.isStatusPopupMinimized()) {
            if (artifactUploadState.getFilesInFailedState().isEmpty()) {
                cleanupStates();
                closeWindow();
            } else {
                maximizeWindow();
            }
        }
    }

    private void cleanupStates() {
        uploads.clear();
        artifactUploadState.clearUploadTempData();
    }

    private void setPopupSizeInMinMode() {
        mainLayout.setWidth("900px");
        mainLayout.setHeight("510px");
    }

    private Button getCloseButton() {
        final Button closeBtn = SPUIComponentProvider.getButton(
                UIComponentIdProvider.UPLOAD_STATUS_POPUP_CLOSE_BUTTON_ID, "", "", "", true, VaadinIcon.CLOSE,
                SPUIButtonStyleNoBorder.class);
        closeBtn.addClassNames("borderless");
        closeBtn.addClickListener(event -> onClose());
        return closeBtn;
    }

    private void onClose() {
        if (artifactUploadState.areAllUploadsFinished()) {
            cleanupStates();
            closeWindow();
        } else {
            minimizeWindow();
        }
    }

    private void closeWindow() {
        // Vaadin 8 setWindowMode(NORMAL): a dialog that is not resizable has
        // no maximized mode
        setPopupSizeInMinMode();
        close();
    }
}
