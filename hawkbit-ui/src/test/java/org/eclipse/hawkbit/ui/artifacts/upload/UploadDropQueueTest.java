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
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BooleanSupplier;

import jakarta.servlet.MultipartConfigElement;

import io.qameta.allure.Feature;
import io.qameta.allure.Story;
import org.eclipse.hawkbit.repository.ArtifactManagement;
import org.eclipse.hawkbit.repository.SoftwareModuleManagement;
import org.eclipse.hawkbit.repository.model.Artifact;
import org.eclipse.hawkbit.repository.model.ArtifactUpload;
import org.eclipse.hawkbit.repository.model.SoftwareModule;
import org.eclipse.hawkbit.ui.artifacts.ArtifactUploadState;
import org.eclipse.hawkbit.ui.artifacts.upload.FileUploadProgress.FileUploadStatus;
import org.eclipse.hawkbit.ui.common.CommonUiDependencies;
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
import com.vaadin.flow.server.Command;
import com.vaadin.flow.server.VaadinSession;
import com.vaadin.flow.server.streams.UploadEvent;

/**
 * 1b-14: several files dropped together. The browser sends up to three upload
 * requests at once; the status window, opened by the first, makes the drop
 * zone inert. The drop handler accepts the others while uploads are listed,
 * streams the files one after the other, keeps the waiting ones queued (the
 * "Clear file upload queue" prompt), and skips a file removed from the queue.
 */
@Feature("Unit Tests - Management UI")
@Story("Upload: multi-file drop")
class UploadDropQueueTest {

    private static final long SM_ID = 1L;
    private static final int FILE_SIZE = 64 * 1024;

    private final ArtifactUploadState state = new ArtifactUploadState();
    private final ArtifactManagement artifactManagement = mock(ArtifactManagement.class);
    private final List<String> stored = Collections.synchronizedList(new ArrayList<>());
    private final ExecutorService repositoryExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService requests = Executors.newCachedThreadPool();
    private final UI ui = mock(UI.class);
    private final VaadinSession session = mock(VaadinSession.class);

    private Object previousContext;
    private UploadDropAreaLayout layout;
    private UploadDropAreaLayout.UploadFileDropHandler handler;

    @BeforeEach
    void setUp() {
        final ApplicationContext context = mock(ApplicationContext.class);
        when(context.getBean(EventBus.UIEventBus.class)).thenReturn(mock(EventBus.UIEventBus.class));
        when(context.getBean(ArtifactUploadState.class)).thenReturn(state);
        when(context.getBean(eq("uiExecutor"), eq(java.util.concurrent.Executor.class)))
                .thenReturn(repositoryExecutor);
        previousContext = ReflectionTestUtils.getField(SpringContextHolder.getInstance(), "context");
        ReflectionTestUtils.setField(SpringContextHolder.getInstance(), "context", context);

        doAnswer(invocation -> {
            ((Command) invocation.getArgument(0)).execute();
            return null;
        }).when(session).accessSynchronously(any());

        final SoftwareModule softwareModule = mock(SoftwareModule.class);
        when(softwareModule.getId()).thenReturn(SM_ID);
        when(softwareModule.getArtifacts()).thenReturn(List.of());
        final SoftwareModuleManagement softwareManagement = mock(SoftwareModuleManagement.class);
        when(softwareManagement.get(SM_ID)).thenReturn(Optional.of(softwareModule));
        when(artifactManagement.create(any())).thenAnswer(invocation -> {
            final ArtifactUpload upload = invocation.getArgument(0);
            final long size = upload.getInputStream().readAllBytes().length;
            stored.add(upload.getFilename());
            final Artifact artifact = mock(Artifact.class);
            when(artifact.getSize()).thenReturn(size);
            return artifact;
        });

        final CommonUiDependencies uiDependencies = mock(CommonUiDependencies.class);
        when(uiDependencies.getI18n()).thenReturn(mock(VaadinMessageSource.class));
        when(uiDependencies.getUiNotification()).thenReturn(mock(UINotification.class));
        when(uiDependencies.getEventBus()).thenReturn(mock(EventBus.UIEventBus.class));
        state.getSmGridLayoutUiState().setSelectedEntityId(SM_ID);

        // the upload managers register their upload URLs with the current UI
        UI.setCurrent(ui);
        layout = new UploadDropAreaLayout(uiDependencies, state,
                new MultipartConfigElement("", 10L * FILE_SIZE, -1L, 0), softwareManagement, artifactManagement);
        handler = layout.new UploadFileDropHandler();
    }

    @AfterEach
    void tearDown() {
        requests.shutdownNow();
        repositoryExecutor.shutdownNow();
        ReflectionTestUtils.setField(SpringContextHolder.getInstance(), "context", previousContext);
        UI.setCurrent(null);
    }

    @Test
    void inertDropZoneAcceptsRequestsOnlyWhileUploadsAreListed() {
        assertThat(handler.isAllowInert()).as("no upload listed").isFalse();

        final FileUploadId id = new FileUploadId("a.bin", softwareModule());
        state.updateFileUploadProgress(id, new FileUploadProgress(id, FileUploadStatus.UPLOAD_STARTED));
        assertThat(handler.isAllowInert()).as("an upload is queued").isTrue();

        state.updateFileUploadProgress(id,
                new FileUploadProgress(id, FileUploadStatus.UPLOAD_SUCCESSFUL, FILE_SIZE, FILE_SIZE));
        assertThat(handler.isAllowInert()).as("all uploads ended").isFalse();
    }

    @Test
    void droppedFilesAreStreamedOneAfterTheOtherAndWaitAsQueued() throws Exception {
        final CountDownLatch releaseFirst = new CountDownLatch(1);
        final Future<?> first = requests.submit(() -> upload("a.bin", new HeldStream(releaseFirst)));
        awaitTrue(() -> status("a.bin") == FileUploadStatus.UPLOAD_IN_PROGRESS);

        final Future<?> second = requests.submit(() -> upload("b.bin", new ByteArrayInputStream(new byte[FILE_SIZE])));
        awaitTrue(() -> dropQueueLock().hasQueuedThreads());

        assertThat(status("b.bin")).as("the second file waits, listed as queued")
                .isEqualTo(FileUploadStatus.UPLOAD_STARTED);
        assertThat(stored).isEmpty();

        releaseFirst.countDown();
        first.get(10, TimeUnit.SECONDS);
        second.get(10, TimeUnit.SECONDS);
        awaitTrue(() -> stored.size() == 2);
        assertThat(stored).containsExactly("a.bin", "b.bin");
    }

    @Test
    void fileRemovedFromTheQueueWhileWaitingIsNotUploaded() throws Exception {
        final CountDownLatch releaseFirst = new CountDownLatch(1);
        final Future<?> first = requests.submit(() -> upload("a.bin", new HeldStream(releaseFirst)));
        awaitTrue(() -> status("a.bin") == FileUploadStatus.UPLOAD_IN_PROGRESS);

        final ByteArrayInputStream secondBody = new ByteArrayInputStream(new byte[FILE_SIZE]);
        final Future<?> second = requests.submit(() -> upload("b.bin", secondBody));
        awaitTrue(() -> dropQueueLock().hasQueuedThreads());

        // "Clear file upload queue" confirmed when leaving the view
        state.clearFileStates();
        releaseFirst.countDown();
        first.get(10, TimeUnit.SECONDS);
        second.get(10, TimeUnit.SECONDS);

        awaitTrue(() -> stored.size() == 1);
        assertThat(stored).as("the running file ends, the queued one is not sent on").containsExactly("a.bin");
        assertThat(secondBody.available()).as("the skipped body is read to its end (200, no error)").isZero();
        assertThat(state.isFileInUploadState(new FileUploadId("b.bin", softwareModule()))).isFalse();
    }

    private Void upload(final String fileName, final InputStream body) throws IOException {
        final UploadEvent event = mock(UploadEvent.class);
        when(event.getUI()).thenReturn(ui);
        when(event.getSession()).thenReturn(session);
        when(event.getFileName()).thenReturn(fileName);
        when(event.getFileSize()).thenReturn((long) FILE_SIZE);
        when(event.getContentType()).thenReturn("application/octet-stream");
        when(event.getInputStream()).thenReturn(body);
        handler.handleUploadRequest(event);
        return null;
    }

    private FileUploadStatus status(final String fileName) {
        final FileUploadProgress progress = state.getFileUploadProgress(new FileUploadId(fileName, softwareModule()));
        return progress == null ? null : progress.getFileUploadStatus();
    }

    private static SoftwareModule softwareModule() {
        final SoftwareModule softwareModule = mock(SoftwareModule.class);
        when(softwareModule.getId()).thenReturn(SM_ID);
        return softwareModule;
    }

    private ReentrantLock dropQueueLock() {
        return (ReentrantLock) ReflectionTestUtils.getField(layout, "dropQueueLock");
    }

    private static void awaitTrue(final BooleanSupplier condition) throws InterruptedException {
        final long end = System.currentTimeMillis() + 10_000;
        while (!condition.getAsBoolean()) {
            assertThat(System.currentTimeMillis()).as("condition not met within 10 s").isLessThan(end);
            Thread.sleep(10);
        }
    }

    /** A request body that delivers half of the file, then waits for a latch. */
    private static final class HeldStream extends InputStream {
        private final CountDownLatch release;
        private int remaining = FILE_SIZE;

        private HeldStream(final CountDownLatch release) {
            this.release = release;
        }

        @Override
        public int read() throws IOException {
            final byte[] one = new byte[1];
            return read(one, 0, 1) < 0 ? -1 : one[0] & 0xff;
        }

        @Override
        public int read(final byte[] buffer, final int offset, final int length) throws IOException {
            if (remaining <= FILE_SIZE / 2) {
                try {
                    if (!release.await(10, TimeUnit.SECONDS)) {
                        throw new IOException("not released");
                    }
                } catch (final InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException(e);
                }
            }
            if (remaining == 0) {
                return -1;
            }
            final int n = Math.min(Math.min(length, remaining), 1024);
            remaining -= n;
            return n;
        }
    }
}
