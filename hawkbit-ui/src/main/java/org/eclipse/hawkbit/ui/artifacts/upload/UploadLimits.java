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

import jakarta.servlet.MultipartConfigElement;

import org.eclipse.hawkbit.repository.QuotaManagement;

/**
 * The size limit the upload components of the browser apply before a file is
 * sent. The server keeps checking (the upload handlers and the repository), so
 * the browser limit only gives earlier feedback; the total storage quota is
 * not checked in the browser, as it depends on what other users store meanwhile.
 */
final class UploadLimits {

    private UploadLimits() {
    }

    /**
     * The largest file the server accepts: the smaller of the servlet's
     * multipart file size limit (checked by the upload handlers) and the
     * maximum artifact size quota (checked by the repository). Values that are
     * not positive mean "no limit".
     *
     * @param multipartMaxFileSize
     *            {@link MultipartConfigElement#getMaxFileSize()}
     * @param quotaMaxArtifactSize
     *            {@link QuotaManagement#getMaxArtifactSize()}
     * @return the limit in bytes, {@code 0} if there is none
     */
    static long maxFileSize(final long multipartMaxFileSize, final long quotaMaxArtifactSize) {
        if (multipartMaxFileSize > 0 && quotaMaxArtifactSize > 0) {
            return Math.min(multipartMaxFileSize, quotaMaxArtifactSize);
        }
        return Math.max(Math.max(multipartMaxFileSize, quotaMaxArtifactSize), 0);
    }
}
