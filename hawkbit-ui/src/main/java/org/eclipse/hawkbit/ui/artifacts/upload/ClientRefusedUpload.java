/**
 * Copyright (c) 2023 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.hawkbit.ui.artifacts.upload;

import java.util.concurrent.locks.Lock;

import org.eclipse.hawkbit.repository.ArtifactManagement;
import org.eclipse.hawkbit.repository.SizeConversionHelper;
import org.eclipse.hawkbit.repository.SoftwareModuleManagement;
import org.eclipse.hawkbit.repository.model.SoftwareModule;
import org.eclipse.hawkbit.ui.artifacts.ArtifactUploadState;
import org.eclipse.hawkbit.ui.utils.UINotification;
import org.eclipse.hawkbit.ui.utils.VaadinMessageSource;

import com.vaadin.flow.component.upload.UploadManager;

/**
 * Lists a file the browser refused because it is larger than the size limit
 * ({@link UploadLimits}) in the upload status window, with the reason the
 * server gives for the same file: the file never reaches the server, so no
 * upload handler sees it. The window shows the file as the handlers do (started,
 * failed, finished) and the status events of the other files of a drop are not
 * disturbed.
 */
final class ClientRefusedUpload extends AbstractFileTransferHandler {

    private static final long serialVersionUID = 1L;

    private final transient SoftwareModuleManagement softwareManagement;
    private final UINotification uiNotification;
    private final long maxFileSize;

    ClientRefusedUpload(final ArtifactManagement artifactManagement, final SoftwareModuleManagement softwareManagement,
            final VaadinMessageSource i18n, final UINotification uiNotification, final Lock uploadLock,
            final long maxFileSize) {
        super(artifactManagement, i18n, uploadLock);
        this.softwareManagement = softwareManagement;
        this.uiNotification = uiNotification;
        this.maxFileSize = maxFileSize;
    }

    /**
     * Shows the refusal of the browser: the file as failed in the status
     * window with the "Maximum artifact size" reason. Other reasons the
     * browser gives (too many files, file type) cannot occur, the limits for
     * them are not set.
     *
     * @param event
     *            the file reject event of an upload manager
     */
    void onFileRejected(final UploadManager.FileRejectedEvent event) {
        if (event.getReason() != UploadManager.FileRejectionReason.FILE_TOO_LARGE) {
            return;
        }
        final ArtifactUploadState state = getUploadState();
        final Long softwareModuleId = state.getSmGridLayoutUiState().getSelectedEntityId();
        final SoftwareModule softwareModule = softwareModuleId == null ? null
                : softwareManagement.get(softwareModuleId).orElse(null);
        if (softwareModule == null) {
            uiNotification.displayValidationError(getI18n().getMessage("message.error.noSwModuleSelected"));
            return;
        }

        final FileUploadId fileUploadId = new FileUploadId(event.getFileName(), softwareModule);
        if (state.isFileInUploadState(fileUploadId)) {
            // the same file is listed already (the server checks this first)
            uiNotification.displayValidationError(getI18n().getMessage("message.no.duplicateFiles"));
            return;
        }
        publishUploadStarted(fileUploadId);
        if (isFileAlreadyContainedInSoftwareModule(fileUploadId, softwareModule)) {
            // the server checks this before the size
            interruptUploadDueToDuplicateFile();
        } else {
            interruptUploadDueToFileSizeQuotaExceeded(SizeConversionHelper.byteValueToReadableString(maxFileSize));
        }
        publishUploadFailedAndFinishedEvent(fileUploadId);
    }
}
