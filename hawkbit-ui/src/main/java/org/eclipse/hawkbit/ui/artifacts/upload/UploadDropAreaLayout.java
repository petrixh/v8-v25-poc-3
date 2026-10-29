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
import org.eclipse.hawkbit.repository.QuotaManagement;
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
    private final long maxFileSize;
    // dropped files are streamed one after the other, in the order their
    // requests arrive
    private final transient Lock dropQueueLock = new ReentrantLock(true);

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
     * @param quotaManagement
     *            the {@link QuotaManagement}: the maximum artifact size is the
     *            limit the browser applies before a file is sent
     */
    public UploadDropAreaLayout(final CommonUiDependencies uiDependencies, final ArtifactUploadState artifactUploadState,
            final MultipartConfigElement multipartConfigElement, final SoftwareModuleManagement softwareManagement,
            final ArtifactManagement artifactManagement,
            final QuotaManagement quotaManagement) {
        this.i18n = uiDependencies.getI18n();
        this.uiNotification = uiDependencies.getUiNotification();
        this.artifactUploadState = artifactUploadState;
        this.multipartConfigElement = multipartConfigElement;
        this.softwareManagement = softwareManagement;
        this.artifactManagement = artifactManagement;

        this.uploadButtonLayout = new UploadProgressButtonLayout(i18n, uiDependencies.getEventBus(), artifactUploadState,
                multipartConfigElement, artifactManagement, softwareManagement, uploadLock, quotaManagement,
                uiNotification);
        this.maxFileSize = UploadLimits.maxFileSize(multipartConfigElement.getMaxFileSize(),
                quotaManagement.getMaxArtifactSize());

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
        // a file over the size limit is refused in the browser, before it is
        // sent, and listed in the upload status window as the server does
        dropUploadManager.setMaxFileSize(maxFileSize);
        final ClientRefusedUpload refusedUpload = new ClientRefusedUpload(artifactManagement, softwareManagement, i18n,
                uiNotification, uploadLock, maxFileSize);
        dropUploadManager.addFileRejectedListener(refusedUpload::onFileRejected);
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

    // package-private for UploadDropQueueTest
    class UploadFileDropHandler implements UploadHandler {

        private static final long serialVersionUID = 1L;

        /**
         * Vaadin 8's modal upload status window did not stop the files of a
         * drop that started before it opened. The window opens with the first
         * file and makes the drop zone inert, and Flow refuses requests to an
         * inert element (403) while the browser still sends the other files of
         * the drop: up to three at once, each further one when an earlier one
         * ends, also after the listed ones have finished and while the window
         * is still open. A modal dialog's curtain keeps a new drop from
         * reaching the drop zone, so a request that arrives while it is inert
         * belongs to a drop the user made before: always accepted. The other
         * checks of the request stay: its security key, and a drop zone that
         * is attached, visible and enabled.
         *
         * @return {@code true}
         */
        @Override
        public boolean isAllowInert() {
            return true;
        }

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
            final AtomicReference<FileTransferHandlerStreamVariable> streamVariable = new AtomicReference<>();
            AbstractFileTransferHandler.accessLocked(event,
                    () -> streamVariable.set(drop(event.getFileName(), event.getFileSize(), event.getContentType())));

            // a refused or failed upload is answered with 200, as in Vaadin 8:
            // the upload status window shows the reason, and a rejection
            // (422) would only add a browser console error
            if (streamVariable.get() == null) {
                AbstractFileTransferHandler.drainQuietly(event.getInputStream());
                return;
            }

            // Vaadin 8 sent the dropped files one after the other; the browser
            // now sends several at once. Each waits here for its turn, listed
            // in the status window as queued, so leaving the view asks to clear
            // the upload queue, as in Vaadin 8
            final boolean removedFromQueue;
            dropQueueLock.lock();
            try {
                removedFromQueue = !streamFile(event, streamVariable.get());
            } finally {
                dropQueueLock.unlock();
            }
            if (removedFromQueue) {
                // the queue was cleared when leaving the view: Vaadin 8 did not
                // send such a file
                AbstractFileTransferHandler.drainQuietly(event.getInputStream());
            }
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
         *
         * @return {@code false} if the file was removed from the upload queue
         *         while it waited, and nothing was streamed
         */
        private boolean streamFile(final UploadEvent event, final FileTransferHandlerStreamVariable streamVariable) {
            final String fileName = event.getFileName();
            final long contentLength = event.getFileSize();

            final AtomicReference<OutputStream> receiverStream = new AtomicReference<>();
            AbstractFileTransferHandler.accessLocked(event, () -> {
                // under the session lock, as the queue is cleared under it:
                // either the file is gone, or it starts and counts as running
                if (streamVariable.isRemovedFromUploadQueue()) {
                    return;
                }
                streamVariable.streamingStarted(fileName);
                receiverStream.set(streamVariable.getOutputStream());
            });
            if (receiverStream.get() == null) {
                return false;
            }

            final OutputStream outputStream = receiverStream.get();
            Exception failure = null;
            try {
                AbstractFileTransferHandler.streamToReceiver(event.getInputStream(), outputStream,
                        streamVariable.listenProgress(),
                        bytesReceived -> AbstractFileTransferHandler.accessLocked(event,
                                () -> streamVariable.onProgress(fileName, bytesReceived, contentLength)),
                        streamVariable::isInterrupted);
            } catch (final IOException | RuntimeException e) {
                failure = e;
                // before the stream ends: the repository thread must not keep
                // a truncated artifact
                streamVariable.interruptUploadBeforeStreamEnd();
            } finally {
                AbstractFileTransferHandler.tryToCloseIOStream(outputStream);
            }

            if (failure == null) {
                AbstractFileTransferHandler.accessLocked(event, () -> streamVariable.streamingFinished(fileName));
            } else {
                final Exception reason = failure;
                AbstractFileTransferHandler.accessLocked(event,
                        () -> streamVariable.streamingFailed(fileName, reason));
                AbstractFileTransferHandler.drainQuietly(event.getInputStream());
            }
            return true;
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
