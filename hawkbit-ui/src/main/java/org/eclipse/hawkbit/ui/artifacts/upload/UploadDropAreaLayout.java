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

import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import jakarta.servlet.MultipartConfigElement;

import org.eclipse.hawkbit.repository.ArtifactManagement;
import org.eclipse.hawkbit.repository.SoftwareModuleManagement;
import org.eclipse.hawkbit.repository.model.SoftwareModule;
import org.eclipse.hawkbit.ui.artifacts.ArtifactUploadState;
import org.eclipse.hawkbit.ui.common.CommonUiDependencies;
import org.eclipse.hawkbit.ui.common.data.proxies.ProxySoftwareModule;
import org.eclipse.hawkbit.ui.common.layout.MasterEntityAwareComponent;
import org.eclipse.hawkbit.ui.utils.UIComponentIdProvider;
import org.eclipse.hawkbit.ui.utils.UIMessageIdProvider;
import org.eclipse.hawkbit.ui.utils.UINotification;
import org.eclipse.hawkbit.ui.utils.VaadinMessageSource;

import com.vaadin.flow.component.Composite;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.upload.UploadDropZone;
import com.vaadin.flow.component.upload.UploadManager;
import com.vaadin.flow.server.streams.UploadEvent;
import com.vaadin.flow.server.streams.UploadHandler;

/**
 * Container for drag and drop area in the upload view.
 *
 * Vaadin 8: a {@code FileDropTarget} on the layout. Flow: the layout inside an
 * {@link UploadDropZone}, whose upload manager sends each dropped file to
 * {@link UploadFileDropHandler}.
 */
public class UploadDropAreaLayout extends Composite<UploadDropZone>
        implements MasterEntityAwareComponent<ProxySoftwareModule> {
    private static final long serialVersionUID = 1L;

    private final VaadinMessageSource i18n;
    private final UINotification uiNotification;

    private final ArtifactUploadState artifactUploadState;

    private final transient ArtifactManagement artifactManagement;
    private final transient SoftwareModuleManagement softwareManagement;

    private final transient MultipartConfigElement multipartConfigElement;
    private final transient Lock uploadLock = new ReentrantLock();

    private final UploadProgressButtonLayout uploadButtonLayout;
    private VerticalLayout dropAreaLayout;
    private UploadDropZone dropZone;

    /**
     * Creates a new {@link UploadDropAreaLayout} instance.
     *
     * @param uiDependencies
     *            the {@link CommonUiDependencies}
     * @param artifactUploadState
     *            the {@link ArtifactUploadState} for state information
     * @param multipartConfigElement
     *            the {@link MultipartConfigElement}
     * @param softwareManagement
     *            the {@link SoftwareModuleManagement} for retrieving the
     *            {@link SoftwareModule}
     * @param artifactManagement
     *            the {@link ArtifactManagement} for storing the uploaded
     *            artifacts
     */
    public UploadDropAreaLayout(final CommonUiDependencies uiDependencies, final ArtifactUploadState artifactUploadState,
            final MultipartConfigElement multipartConfigElement, final SoftwareModuleManagement softwareManagement,
            final ArtifactManagement artifactManagement) {
        this.i18n = uiDependencies.getI18n();
        this.uiNotification = uiDependencies.getUiNotification();
        this.artifactUploadState = artifactUploadState;
        this.multipartConfigElement = multipartConfigElement;
        this.softwareManagement = softwareManagement;
        this.artifactManagement = artifactManagement;

        this.uploadButtonLayout = new UploadProgressButtonLayout(i18n, uiDependencies.getEventBus(), artifactUploadState,
                multipartConfigElement, artifactManagement, softwareManagement, uploadLock);

        buildLayout();
    }

    private void buildLayout() {
        dropAreaLayout = new VerticalLayout();
        dropAreaLayout.setId(UIComponentIdProvider.UPLOAD_ARTIFACT_FILE_DROP_LAYOUT);
        dropAreaLayout.setPadding(false);
        dropAreaLayout.setSpacing(false);
        dropAreaLayout.addClassNames("upload-drop-area-layout-info");
        dropAreaLayout.setEnabled(false);
        dropAreaLayout.setHeight(null);

        final Span dropIcon = new Span(VaadinIcon.ARROW_DOWN.create());
        dropIcon.addClassNames("drop-icon");
        dropIcon.setWidth(null);
        dropAreaLayout.add(dropIcon);

        final Span dropHereLabel = new Span(i18n.getMessage(UIMessageIdProvider.LABEL_DROP_AREA_UPLOAD));
        dropHereLabel.setWidth(null);
        dropAreaLayout.add(dropHereLabel);

        uploadButtonLayout.setWidth(null);
        uploadButtonLayout.addClassNames("upload-button");
        dropAreaLayout.add(uploadButtonLayout);

        final UploadManager dropUploadManager = new UploadManager(dropAreaLayout, new UploadFileDropHandler());
        // the progress window shows the files; the drop zone keeps no list
        dropUploadManager.addAllFinishedListener(event -> dropUploadManager.clearFileList());
        dropZone = new UploadDropZone(dropAreaLayout, dropUploadManager);
        dropZone.setEnabled(false);
    }

    @Override
    protected UploadDropZone initContent() {
        return dropZone;
    }

    /**
     * Update the upload view on file drop
     *
     * @param masterEntity
     *            ProxySoftwareModule
     */
    @Override
    public void masterEntityChanged(final ProxySoftwareModule masterEntity) {
        final Long masterEntityId = masterEntity != null ? masterEntity.getId() : null;

        dropZone.setEnabled(masterEntityId != null);
        dropAreaLayout.setEnabled(masterEntityId != null);
        uploadButtonLayout.updateMasterEntityFilter(masterEntityId);
    }

    /**
     * Checks progress on file upload
     *
     * @param fileUploadProgress
     *            FileUploadProgress
     */
    public void onUploadChanged(final FileUploadProgress fileUploadProgress) {
        uploadButtonLayout.onUploadChanged(fileUploadProgress);
    }

    /**
     * Is called when view is shown to the user
     */
    public void restoreState() {
        uploadButtonLayout.restoreState();
    }

    /**
     * Get file drop area layout
     *
     * @return VerticalLayout
     */
    public VerticalLayout getDropAreaLayout() {
        return dropAreaLayout;
    }

    /**
     * Get File upload button layout
     *
     * @return UploadProgressButtonLayout
     */
    public UploadProgressButtonLayout getUploadButtonLayout() {
        return uploadButtonLayout;
    }

    private class UploadFileDropHandler implements UploadHandler {

        private static final long serialVersionUID = 1L;

        /**
         * Validates the dropped file and uploads it. Vaadin 8 got all dropped
         * files in one drop event and then streamed each through its stream
         * variable; Flow sends one upload request per file, so the checks run
         * per file. Each step runs with the session lock held, as in Vaadin 8.
         *
         * @param event
         *            UploadEvent of one dropped file
         * @throws IOException
         *             never: stream failures end as a failed upload
         */
        @Override
        public void handleUploadRequest(final UploadEvent event) throws IOException {
            final UI ui = event.getUI();
            final AtomicReference<FileTransferHandlerStreamVariable> streamVariable = new AtomicReference<>();
            ui.accessSynchronously(
                    () -> streamVariable.set(drop(event.getFileName(), event.getFileSize(), event.getContentType())));

            if (streamVariable.get() == null) {
                event.reject();
                return;
            }
            streamFile(ui, event, streamVariable.get());
        }

        private FileTransferHandlerStreamVariable drop(final String fileName, final long fileSize,
                final String mimeType) {
            if (validate()) {
                // selected software module at the time of file drop is
                // considered for upload
                final Long lastSelectedSmId = artifactUploadState.getSmGridLayoutUiState().getSelectedEntityId();
                if (lastSelectedSmId != null) {
                    return uploadFileForSoftwareModule(fileName, fileSize, mimeType, lastSelectedSmId);
                }
            }
            return null;
        }

        private FileTransferHandlerStreamVariable uploadFileForSoftwareModule(final String fileName,
                final long fileSize, final String mimeType, final Long softwareModuleId) {
            final SoftwareModule softwareModule = softwareManagement.get(softwareModuleId).orElse(null);

            if (artifactUploadState.isFileInUploadState(fileName, softwareModule)) {
                uiNotification.displayValidationError(i18n.getMessage("message.no.duplicateFiles"));
                return null;
            }
            return new FileTransferHandlerStreamVariable(fileName, fileSize, multipartConfigElement.getMaxFileSize(),
                    mimeType, softwareModule, artifactManagement, i18n, uploadLock);
        }

        /**
         * What the Vaadin 8 framework did with the stream variable of a
         * dropped file: started, output stream, progress, then finished or
         * failed.
         */
        private void streamFile(final UI ui, final UploadEvent event,
                final FileTransferHandlerStreamVariable streamVariable) {
            final String fileName = event.getFileName();
            final long contentLength = event.getFileSize();

            final AtomicReference<OutputStream> receiverStream = new AtomicReference<>();
            ui.accessSynchronously(() -> {
                streamVariable.streamingStarted(fileName);
                receiverStream.set(streamVariable.getOutputStream());
            });

            Exception failure = null;
            try (OutputStream outputStream = receiverStream.get()) {
                AbstractFileTransferHandler.streamToReceiver(event.getInputStream(), outputStream,
                        streamVariable.listenProgress(),
                        bytesReceived -> ui.accessSynchronously(
                                () -> streamVariable.onProgress(fileName, bytesReceived, contentLength)),
                        streamVariable::isInterrupted);
            } catch (final IOException | RuntimeException e) {
                failure = e;
            }

            if (failure == null) {
                ui.accessSynchronously(() -> streamVariable.streamingFinished(fileName));
            } else {
                final Exception reason = failure;
                ui.accessSynchronously(() -> streamVariable.streamingFailed(fileName, reason));
                event.reject();
            }
        }

        private boolean validate() {
            // Vaadin 8 also refused drops without files ("message.drop.onlyFiles");
            // an upload drop zone only ever sends files. Check if software
            // module is selected.
            return validateSoftwareModuleSelection();
        }

        private boolean validateSoftwareModuleSelection() {
            final Long lastSelectedSmId = artifactUploadState.getSmGridLayoutUiState().getSelectedEntityId();

            if (lastSelectedSmId == null) {
                uiNotification.displayValidationError(i18n.getMessage("message.error.noSwModuleSelected"));
                return false;
            }

            return true;
        }
    }
}
