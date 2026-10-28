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

import com.vaadin.flow.component.upload.UploadButton;
import com.vaadin.flow.component.upload.UploadManager;

/**
 * Vaadin 8: the Upload class had a bug: the lifecycle methods of the
 * registered handler (started, progress, ...) were called even when the upload
 * was interrupted. This class fixed it by not listening to progress and reporting the stream as
 * interrupted once {@link #interruptUpload()} was called, until the streaming
 * failed.
 *
 * Flow: the upload button of an {@link UploadManager}. Its handler
 * ({@link FileTransferHandlerVaadinUpload}) runs the lifecycle itself and asks
 * this class whether the upload is interrupted, so the fix keeps its meaning:
 * only {@link #interruptUpload()} stops the stream.
 *
 */
public class UploadFixed extends UploadButton {

    private static final long serialVersionUID = 1L;

    private volatile boolean uploadInterrupted;

    /**
     * Constructor.
     *
     * @param caption
     *            button caption
     * @param uploadManager
     *            the upload manager whose file picker the button opens
     */
    public UploadFixed(final String caption, final UploadManager uploadManager) {
        super(caption, uploadManager);
    }

    /**
     * Stops the file upload
     */
    public void interruptUpload() {
        uploadInterrupted = true;
    }

    /**
     * @return whether the current upload is interrupted (no more progress is
     *         listened to and the stream stops)
     */
    public boolean isInterrupted() {
        return uploadInterrupted;
    }

    /**
     * The streaming failed: the next upload is not interrupted (Vaadin 8:
     * StreamVariableFixed.streamingFailed).
     */
    void streamingFailed() {
        uploadInterrupted = false;
    }
}
