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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;

import io.qameta.allure.Feature;
import io.qameta.allure.Story;
import org.eclipse.hawkbit.repository.ArtifactManagement;
import org.eclipse.hawkbit.repository.SoftwareModuleManagement;
import org.eclipse.hawkbit.repository.model.SoftwareModule;
import org.eclipse.hawkbit.ui.artifacts.ArtifactUploadState;
import org.eclipse.hawkbit.ui.artifacts.upload.FileUploadProgress.FileUploadStatus;
import org.eclipse.hawkbit.ui.utils.SpringContextHolder;
import org.eclipse.hawkbit.ui.utils.UINotification;
import org.eclipse.hawkbit.ui.utils.VaadinMessageSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.test.util.ReflectionTestUtils;
import org.vaadin.spring.events.EventBus;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.upload.UploadManager;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The size limit the browser applies before a file is sent: how it is read from
 * the system limits, and how a file the browser refused is listed.
 */
@Feature("Unit Tests - Management UI")
@Story("Upload: size limit in the browser")
class UploadSizeLimitTest {

    private static final long SM_ID = 1L;
    private static final long LIMIT = 10L * 1024 * 1024;

    private final ArtifactUploadState state = new ArtifactUploadState();
    private final VaadinMessageSource i18n = mock(VaadinMessageSource.class);
    private final UINotification notification = mock(UINotification.class);
    private final SoftwareModule softwareModule = mock(SoftwareModule.class);

    private Object previousContext;
    private ClientRefusedUpload refused;

    @Test
    void theLimitIsTheSmallerOfTheMultipartLimitAndTheArtifactQuota() {
        assertThat(UploadLimits.maxFileSize(1024, 2048)).isEqualTo(1024);
        assertThat(UploadLimits.maxFileSize(2048, 1024)).isEqualTo(1024);
        assertThat(UploadLimits.maxFileSize(1024, 1024)).isEqualTo(1024);
    }

    @Test
    void aValueThatIsNotPositiveMeansNoLimit() {
        assertThat(UploadLimits.maxFileSize(-1, 2048)).isEqualTo(2048);
        assertThat(UploadLimits.maxFileSize(1024, 0)).isEqualTo(1024);
        assertThat(UploadLimits.maxFileSize(-1, -1)).isZero();
        assertThat(UploadLimits.maxFileSize(0, 0)).isZero();
    }

    @Test
    void managerLimitIsInBytesAndZeroMeansUnlimited() {
        final UI ui = mock(UI.class);
        UI.setCurrent(ui);
        try {
            final UploadManager manager = new UploadManager(new com.vaadin.flow.component.html.Div());
            manager.setMaxFileSize(UploadLimits.maxFileSize(LIMIT, 2 * LIMIT));
            assertThat(manager.getMaxFileSize()).isEqualTo(LIMIT);
            manager.setMaxFileSize(UploadLimits.maxFileSize(-1, -1));
            assertThat(manager.getMaxFileSize()).isZero();
        } finally {
            UI.setCurrent(null);
        }
    }

    @BeforeEach
    void setUp() {
        final ApplicationContext context = mock(ApplicationContext.class);
        when(context.getBean(EventBus.UIEventBus.class)).thenReturn(mock(EventBus.UIEventBus.class));
        when(context.getBean(ArtifactUploadState.class)).thenReturn(state);
        previousContext = ReflectionTestUtils.getField(SpringContextHolder.getInstance(), "context");
        ReflectionTestUtils.setField(SpringContextHolder.getInstance(), "context", context);

        when(softwareModule.getId()).thenReturn(SM_ID);
        when(softwareModule.getArtifacts()).thenReturn(java.util.List.of());
        final SoftwareModuleManagement softwareManagement = mock(SoftwareModuleManagement.class);
        when(softwareManagement.get(SM_ID)).thenReturn(Optional.of(softwareModule));
        when(i18n.getMessage(eq("message.upload.fileSizeQuota"), any(Object[].class)))
                .thenAnswer(invocation -> "Maximum artifact size (" + invocation.getArgument(1) + ") exceeded");
        when(i18n.getMessage("message.no.duplicateFiles")).thenReturn("Duplicate files selected");
        when(i18n.getMessage("message.error.noSwModuleSelected")).thenReturn("No module selected");
        state.getSmGridLayoutUiState().setSelectedEntityId(SM_ID);

        refused = new ClientRefusedUpload(mock(ArtifactManagement.class), softwareManagement, i18n, notification,
                new ReentrantLock(), LIMIT);
    }

    @AfterEach
    void tearDown() {
        ReflectionTestUtils.setField(SpringContextHolder.getInstance(), "context", previousContext);
    }

    @Test
    void aTooLargeFileIsListedAsFailedWithTheServersReason() {
        refused.onFileRejected(rejected("big.bin", UploadManager.FileRejectionReason.FILE_TOO_LARGE));

        final FileUploadProgress progress = state.getFileUploadProgress(new FileUploadId("big.bin", softwareModule));
        assertThat(progress.getFileUploadStatus()).isEqualTo(FileUploadStatus.UPLOAD_FAILED);
        assertThat(progress.getFailureReason()).isEqualTo("Maximum artifact size (10 MB) exceeded");
    }

    @Test
    void aFileAlreadyListedGetsTheDuplicateMessageAndKeepsItsRow() {
        final FileUploadId id = new FileUploadId("a.bin", softwareModule);
        final FileUploadProgress running = new FileUploadProgress(id, FileUploadStatus.UPLOAD_IN_PROGRESS, 1, 2);
        state.updateFileUploadProgress(id, running);

        refused.onFileRejected(rejected("a.bin", UploadManager.FileRejectionReason.FILE_TOO_LARGE));

        assertThat(state.getFileUploadProgress(id)).isSameAs(running);
        verify(notification).displayValidationError("Duplicate files selected");
    }

    @Test
    void withoutASelectedModuleTheUserIsToldAndNothingIsListed() {
        state.getSmGridLayoutUiState().setSelectedEntityId(null);

        refused.onFileRejected(rejected("big.bin", UploadManager.FileRejectionReason.FILE_TOO_LARGE));

        verify(notification).displayValidationError("No module selected");
        assertThat(state.isFileInUploadState(new FileUploadId("big.bin", softwareModule))).isFalse();
    }

    @Test
    void otherRejectionReasonsAreNotListed() {
        refused.onFileRejected(rejected("a.bin", UploadManager.FileRejectionReason.TOO_MANY_FILES));

        assertThat(state.isFileInUploadState(new FileUploadId("a.bin", softwareModule))).isFalse();
    }

    @Test
    void droppedFilesOverTheLimitAreListedAndTheOthersAreLeftAlone() {
        refused.onFilesDropped(dropped("a.bin", LIMIT - 1, "big1.bin", LIMIT + 1, "exact.bin", LIMIT, "big2.bin",
                5 * LIMIT));

        final FileUploadProgress big1 = state.getFileUploadProgress(new FileUploadId("big1.bin", softwareModule));
        assertThat(big1.getFileUploadStatus()).isEqualTo(FileUploadStatus.UPLOAD_FAILED);
        assertThat(big1.getFailureReason()).isEqualTo("Maximum artifact size (10 MB) exceeded");
        assertThat(state.getFileUploadProgress(new FileUploadId("big2.bin", softwareModule)).getFileUploadStatus())
                .isEqualTo(FileUploadStatus.UPLOAD_FAILED);
        assertThat(state.isFileInUploadState(new FileUploadId("a.bin", softwareModule))).isFalse();
        assertThat(state.isFileInUploadState(new FileUploadId("exact.bin", softwareModule))).isFalse();
    }

    @Test
    void aDropWithoutFileDataOrWithoutALimitListsNothing() {
        refused.onFilesDropped(JsonMapper.builder().build().readTree("{}"));
        final ClientRefusedUpload unlimited = new ClientRefusedUpload(mock(ArtifactManagement.class),
                mock(SoftwareModuleManagement.class), i18n, notification, new ReentrantLock(), 0);
        unlimited.onFilesDropped(dropped("big.bin", 5 * LIMIT));

        assertThat(state.isFileInUploadState(new FileUploadId("big.bin", softwareModule))).isFalse();
    }

    /** The event data of a drop: name and size of each file. */
    private static JsonNode dropped(final Object... nameAndSize) {
        final StringBuilder files = new StringBuilder();
        for (int i = 0; i < nameAndSize.length; i += 2) {
            files.append(i > 0 ? "," : "").append("{\"name\":\"").append(nameAndSize[i]).append("\",\"size\":")
                    .append(nameAndSize[i + 1]).append('}');
        }
        return JsonMapper.builder().build()
                .readTree("{\"" + ClientRefusedUpload.DROPPED_FILES.replace("\"", "\\\"") + "\":[" + files + "]}");
    }

    private static UploadManager.FileRejectedEvent rejected(final String fileName,
            final UploadManager.FileRejectionReason reason) {
        return new UploadManager.FileRejectedEvent(new com.vaadin.flow.component.html.Div(), true, reason, fileName);
    }
}
