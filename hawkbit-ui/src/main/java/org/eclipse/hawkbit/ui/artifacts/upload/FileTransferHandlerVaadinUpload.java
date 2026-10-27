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
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;

import org.eclipse.hawkbit.repository.ArtifactManagement;
import org.eclipse.hawkbit.repository.RegexCharacterCollection;
import org.eclipse.hawkbit.repository.SizeConversionHelper;
import org.eclipse.hawkbit.repository.SoftwareModuleManagement;
import org.eclipse.hawkbit.repository.model.SoftwareModule;
import org.eclipse.hawkbit.ui.utils.VaadinMessageSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.io.ByteStreams;
import com.vaadin.flow.server.streams.UploadEvent;
import com.vaadin.flow.server.streams.UploadHandler;

/**
 * Upload handler of the upload button to read and upload a file. One instance
 * is used to handle all the uploads.
 *
 * The handler manages the output to the user and at the same time ensures that
 * the upload does not exceed the configured max file size.
 *
 * Vaadin 8: a Receiver and the started, progress, succeeded, failed and
 * finished listeners of the {@link UploadFixed} upload. Flow: one
 * {@link UploadHandler} that calls the same methods in the same order, each
 * with the session lock held as Vaadin 8 did (the upload request itself runs
 * outside the lock).
 *
 */
public class FileTransferHandlerVaadinUpload extends AbstractFileTransferHandler implements UploadHandler {

    private static final long serialVersionUID = 1L;

    private static final Logger LOG = LoggerFactory.getLogger(FileTransferHandlerVaadinUpload.class);

    private final transient SoftwareModuleManagement softwareModuleManagement;
    private final long maxSize;
    private final UploadFixed upload;

    private FileUploadId fileUploadId;

    FileTransferHandlerVaadinUpload(final long maxSize, final SoftwareModuleManagement softwareManagement,
            final ArtifactManagement artifactManagement, final VaadinMessageSource i18n, final Lock uploadLock,
            final UploadFixed upload) {
        super(artifactManagement, i18n, uploadLock);

        this.maxSize = maxSize;
        this.softwareModuleManagement = softwareManagement;
        this.upload = upload;
    }

    private synchronized FileUploadId setFileUploadId(final String fileName, final SoftwareModule softwareModule) {
        this.fileUploadId = new FileUploadId(fileName, softwareModule);
        return fileUploadId;
    }

    /**
     * Runs the Vaadin 8 upload lifecycle for one file: started, receive,
     * progress, then succeeded or failed, then finished.
     *
     * @param event
     *            the upload event
     * @throws IOException
     *             never: stream failures end as a failed upload
     */
    @Override
    public void handleUploadRequest(final UploadEvent event) throws IOException {
        final String fileName = event.getFileName();
        final long contentLength = event.getFileSize();

        accessLocked(event, () -> uploadStarted(fileName));

        final AtomicReference<OutputStream> receiverStream = new AtomicReference<>();
        accessLocked(event, () -> receiverStream.set(receiveUpload(fileName, event.getContentType())));

        final OutputStream outputStream = receiverStream.get();
        Exception failure = null;
        try {
            // Vaadin 8 UploadFixed: no progress once the upload is interrupted
            streamToReceiver(event.getInputStream(), outputStream, !upload.isInterrupted(),
                    bytesReceived -> accessLocked(event, () -> updateProgress(bytesReceived, contentLength)),
                    upload::isInterrupted);
        } catch (final IOException | RuntimeException e) {
            failure = e;
            // before the stream ends: the repository thread must not keep a
            // truncated artifact
            interruptUploadBeforeStreamEnd();
        } finally {
            tryToCloseIOStream(outputStream);
        }

        if (failure == null) {
            accessLocked(event, () -> uploadSucceeded(fileName));
        } else {
            final Exception reason = failure;
            accessLocked(event, () -> uploadFailed(fileName, reason));
            upload.streamingFailed();
            event.reject();
        }
        accessLocked(event, () -> uploadFinished(fileName));
    }

    /**
     * Upload started.
     *
     * @param fileName
     *            name of the uploaded file
     */
    public void uploadStarted(final String fileName) {
        // reset internal state here because instance is reused for next upload!
        resetState();

        final SoftwareModule softwareModule = getSelectedSoftwareModule();
        final FileUploadId fupId = setFileUploadId(fileName, softwareModule);

        if (getUploadState().isFileInUploadState(this.fileUploadId)) {
            // actual interrupt will happen a bit late so setting the below
            // flag
            interruptUploadDueToDuplicateFile();
            upload.interruptUpload();
        } else {
            publishUploadStarted(fupId);

            if (RegexCharacterCollection.stringContainsCharacter(fileName, ILLEGAL_FILENAME_CHARACTERS)) {
                LOG.debug("Filename contains illegal characters {} for upload {}", fupId.getFilename(), fupId);
                interruptUploadDueToIllegalFilename();
                upload.interruptUpload();
            } else if (isFileAlreadyContainedInSoftwareModule(fupId, softwareModule)) {
                LOG.debug("File {} already contained in Software Module {}", fupId.getFilename(),
                        softwareModule);
                interruptUploadDueToDuplicateFile();
                upload.interruptUpload();
            }
        }
    }

    private SoftwareModule getSelectedSoftwareModule() {
        final Long lastSelectedSmId = getUploadState().getSmGridLayoutUiState().getSelectedEntityId();

        return softwareModuleManagement.get(lastSelectedSmId)
                .orElseThrow(() -> new IllegalStateException("SoftwareModul with unknown ID selected"));
    }

    /**
     * Create the stream the upload is written to.
     *
     * @param fileName
     *            name of the uploaded file
     * @param mimeType
     *            MIME type of the uploaded file
     * @return the stream
     */
    public OutputStream receiveUpload(final String fileName, final String mimeType) {
        if (isUploadInterrupted()) {
            return ByteStreams.nullOutputStream();
        }

        // we return the outputstream so we cannot close it here
        @SuppressWarnings("squid:S2095")
        final PipedOutputStream outputStream = new PipedOutputStream();
        try {
            PipedInputStream inputStream = new PipedInputStream(outputStream);
            publishUploadProgressEvent(fileUploadId, 0, 0);
            startTransferToRepositoryThread(inputStream, fileUploadId, mimeType);
        } catch (final IOException e) {
            LOG.warn("Creating piped Stream failed!", e);
            tryToCloseIOStream(outputStream);
            // input stream is not created in case of an IOException
            interruptUploadDueToUploadFailed();
            publishUploadFailedAndFinishedEvent(fileUploadId);
            return ByteStreams.nullOutputStream();
        }
        return outputStream;
    }

    /**
     * Reports progress.
     *
     * @param readBytes
     *            bytes received so far
     * @param contentLength
     *            size of the file
     */
    public void updateProgress(final long readBytes, final long contentLength) {
        if (isUploadInterrupted()) {
            publishUploadFailedAndFinishedEvent(fileUploadId);
            return;
        }

        if (readBytes > maxSize || contentLength > maxSize) {
            interruptUploadDueToFileSizeQuotaExceeded(SizeConversionHelper.byteValueToReadableString(maxSize));
            return;
        }

        publishUploadProgressEvent(fileUploadId, readBytes, contentLength);
    }

    /**
     * Upload successful.
     *
     * @param fileName
     *            name of the uploaded file
     */
    public void uploadSucceeded(final String fileName) {
        if (isUploadInterrupted()) {
            // Upload interruption is delayed maybe another event is fired
            // before
            return;
        }
        assertStateConsistency(fileUploadId, fileName);
    }

    /**
     * Upload finished. Both for good and error variant.
     *
     * @param fileName
     *            name of the uploaded file
     */
    public void uploadFinished(final String fileName) {
        // ignore this event
    }

    /**
     * Upload failed.
     *
     * @param fileName
     *            name of the uploaded file
     * @param reason
     *            the exception that ended the upload
     */
    public void uploadFailed(final String fileName, final Exception reason) {
        assertStateConsistency(fileUploadId, fileName);

        if (!isUploadInterrupted()) {
            interruptUploadDueToUploadFailed();
        }
        LOG.debug("Upload of file {} failed due to following exception: {}", fileUploadId, reason);
        publishUploadFailedAndFinishedEvent(fileUploadId);
    }

    @Override
    protected void resetState() {
        super.resetState();
        this.fileUploadId = null;
    }
}
