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
import java.util.concurrent.locks.Lock;

import org.eclipse.hawkbit.repository.ArtifactManagement;
import org.eclipse.hawkbit.repository.RegexCharacterCollection;
import org.eclipse.hawkbit.repository.SizeConversionHelper;
import org.eclipse.hawkbit.repository.model.SoftwareModule;
import org.eclipse.hawkbit.ui.utils.VaadinMessageSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.io.ByteStreams;

/**
 * Stream variable to read and upload a dropped file. One instance per file
 * stream is used.
 *
 * The handler manages the output to the user and at the same time ensures that
 * the upload does not exceed the configured max file size.
 *
 * Vaadin 8: a {@code com.vaadin.server.StreamVariable} that the framework
 * called for each dropped file. Flow has no stream variables: the drop zone's
 * upload handler ({@link UploadDropAreaLayout}) calls the same methods in the
 * same order, each with the session lock held as Vaadin 8 did.
 *
 */
public class FileTransferHandlerStreamVariable extends AbstractFileTransferHandler {

    private static final long serialVersionUID = 1L;

    private static final Logger LOG = LoggerFactory.getLogger(FileTransferHandlerStreamVariable.class);

    private final long maxSize;
    private final long fileSize;
    private final String mimeType;
    private final FileUploadId fileUploadId;

    private final SoftwareModule selectedSoftwareModule;

    FileTransferHandlerStreamVariable(final String fileName, final long fileSize, final long maxSize,
            final String mimeType, final SoftwareModule selectedSw, final ArtifactManagement artifactManagement,
            final VaadinMessageSource i18n, final Lock uploadLock) {
        super(artifactManagement, i18n, uploadLock);
        this.fileSize = fileSize;
        this.maxSize = maxSize;
        this.mimeType = mimeType;
        this.selectedSoftwareModule = selectedSw;
        this.fileUploadId = new FileUploadId(fileName, selectedSw);

        publishUploadStarted(fileUploadId);
    }

    /**
     * Checks for duplication and invalid file name during file upload process
     * 
     * @param fileName
     *            name of the dropped file
     */
    public void streamingStarted(final String fileName) {
        assertStateConsistency(fileUploadId, fileName);

        if (RegexCharacterCollection.stringContainsCharacter(fileName, ILLEGAL_FILENAME_CHARACTERS)) {
            LOG.debug("Filename contains illegal characters {} for upload {}", fileUploadId.getFilename(),
                    fileUploadId);
            interruptUploadDueToIllegalFilename();
        } else if (isFileAlreadyContainedInSoftwareModule(fileUploadId, selectedSoftwareModule)) {
            LOG.debug("File {} already contained in Software Module {}", fileUploadId.getFilename(),
                    selectedSoftwareModule);
            interruptUploadDueToDuplicateFile();
        }
    }

    /**
     * get the output stream of uploaded file
     *
     * @return OutputStream
     */
    public final OutputStream getOutputStream() {
        if (isUploadInterrupted()) {
            return ByteStreams.nullOutputStream();
        }

        // we return the output stream - we cannot close it here
        @SuppressWarnings("squid:S2095")
        final PipedOutputStream outputStream = new PipedOutputStream();
        final PipedInputStream inputStream;
        try {
            inputStream = new PipedInputStream(outputStream);
            publishUploadProgressEvent(fileUploadId, 0, fileSize);
            startTransferToRepositoryThread(inputStream, fileUploadId, mimeType);
        } catch (final IOException e) {
            LOG.warn("Creating piped Stream failed {}.", e.getMessage());
            tryToCloseIOStream(outputStream);
            // input stream is not created in case of and IOException here
            interruptUploadDueToUploadFailed();
            publishUploadFailedAndFinishedEvent(fileUploadId);
            return ByteStreams.nullOutputStream();
        }
        return outputStream;
    }

    /**
     * listen progress.
     * 
     * @return boolean
     */
    public boolean listenProgress() {
        return true;
    }

    /**
     * Reports progress. Interrupts when the file is too large.
     *
     * @param fileName
     *            name of the dropped file
     * @param bytesReceived
     *            bytes received so far
     * @param contentLength
     *            size of the file
     */
    public void onProgress(final String fileName, final long bytesReceived, final long contentLength) {
        assertStateConsistency(fileUploadId, fileName);

        if (isUploadInterrupted()) {
            publishUploadFailedAndFinishedEvent(fileUploadId);
            return;
        }

        if (bytesReceived > maxSize || contentLength > maxSize) {
            interruptUploadDueToFileSizeQuotaExceeded(SizeConversionHelper.byteValueToReadableString(maxSize));
            return;
        }

        publishUploadProgressEvent(fileUploadId, bytesReceived, contentLength);
    }

    /**
     * Upload finished. Called only in good case.
     *
     * @param fileName
     *            name of the dropped file
     */
    public void streamingFinished(final String fileName) {
        assertStateConsistency(fileUploadId, fileName);
    }

    /**
     * Upload failed.
     *
     * @param fileName
     *            name of the dropped file
     * @param exception
     *            the exception that ended the upload
     */
    public void streamingFailed(final String fileName, final Exception exception) {
        assertStateConsistency(fileUploadId, fileName);

        if (!isUploadInterrupted()) {
            interruptUploadDueToUploadFailed();
        }
        LOG.debug("Streaming of file {} failed due to following exception: {}", fileUploadId, exception);
        publishUploadFailedAndFinishedEvent(fileUploadId);
    }

    /**
     * Verify if upload process is interrupted
     *
     * @return boolean
     */
    public boolean isInterrupted() {
        return isUploadInterrupted();
    }
}
