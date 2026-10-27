/**
 * Copyright (c) 2020 Bosch.IO GmbH and others
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.hawkbit.ui.artifacts.details;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collection;
import java.util.Optional;
import java.util.stream.Collectors;

import org.eclipse.hawkbit.artifact.repository.model.DbArtifact;
import org.eclipse.hawkbit.repository.ArtifactManagement;
import org.eclipse.hawkbit.ui.common.CommonUiDependencies;
import org.eclipse.hawkbit.ui.common.builder.GridComponentBuilder;
import org.eclipse.hawkbit.ui.common.data.mappers.ArtifactToProxyArtifactMapper;
import org.eclipse.hawkbit.ui.common.data.providers.ArtifactDataProvider;
import org.eclipse.hawkbit.ui.common.data.proxies.ProxyArtifact;
import org.eclipse.hawkbit.ui.common.data.proxies.ProxyIdentifiableEntity;
import org.eclipse.hawkbit.ui.common.data.proxies.ProxySoftwareModule;
import org.eclipse.hawkbit.ui.common.event.EntityModifiedEventPayload;
import org.eclipse.hawkbit.ui.common.event.EntityModifiedEventPayload.EntityModifiedEventType;
import org.eclipse.hawkbit.ui.common.event.EventTopics;
import org.eclipse.hawkbit.ui.common.event.FilterType;
import org.eclipse.hawkbit.ui.common.grid.AbstractGrid;
import org.eclipse.hawkbit.ui.common.grid.support.DeleteSupport;
import org.eclipse.hawkbit.ui.common.grid.support.FilterSupport;
import org.eclipse.hawkbit.ui.common.grid.support.MasterEntitySupport;
import org.eclipse.hawkbit.ui.utils.SPUIStyleDefinitions;
import org.eclipse.hawkbit.ui.utils.UIComponentIdProvider;
import org.eclipse.hawkbit.ui.utils.UIMessageIdProvider;
import org.eclipse.hawkbit.ui.utils.UINotification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.AttachmentType;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.server.HttpStatusCode;
import com.vaadin.flow.server.streams.DownloadHandler;

/**
 * Artifact Details grid which is shown on the Upload View.
 */
public class ArtifactDetailsGrid extends AbstractGrid<ProxyArtifact, Long> {
    private static final long serialVersionUID = 1L;

    private static final Logger LOG = LoggerFactory.getLogger(ArtifactDetailsGrid.class);

    private static final String ARTIFACT_NAME_ID = "artifactName";
    private static final String ARTIFACT_SIZE_ID = "artifactSize";
    private static final String ARTIFACT_MODIFIED_DATE_ID = "artifactModifiedDate";
    private static final String ARTIFACT_SHA1_ID = "artifactSha1";
    private static final String ARTIFACT_MD5_ID = "artifactMd5";
    private static final String ARTIFACT_SHA256_ID = "artifactSha256";
    private static final String ARTIFACT_DOWNLOAD_BUTTON_ID = "artifactDownloadButton";
    private static final String ARTIFACT_DELETE_BUTTON_ID = "artifactDeleteButton";

    private final UINotification notification;
    private final transient ArtifactManagement artifactManagement;

    private final transient DeleteSupport<ProxyArtifact> artifactDeleteSupport;
    private final transient MasterEntitySupport<ProxySoftwareModule> masterEntitySupport;

    private boolean artifactsEncrypted;

    /**
     * Constructor
     *
     * @param uiDependencies
     *            {@link CommonUiDependencies}
     * @param artifactManagement
     *            ArtifactManagement
     */
    public ArtifactDetailsGrid(final CommonUiDependencies uiDependencies, final ArtifactManagement artifactManagement) {
        super(uiDependencies.getI18n(), uiDependencies.getEventBus(), uiDependencies.getPermChecker());

        this.notification = uiDependencies.getUiNotification();
        this.artifactManagement = artifactManagement;

        this.artifactDeleteSupport = new DeleteSupport<>(this, i18n, notification, "artifact.details.header",
                "caption.artifacts", ProxyArtifact::getFilename, this::artifactsDeletionCallback,
                UIComponentIdProvider.ARTIFACT_DELETE_CONFIRMATION_DIALOG);

        setFilterSupport(
                new FilterSupport<>(new ArtifactDataProvider(artifactManagement, new ArtifactToProxyArtifactMapper())));
        initFilterMappings();

        this.masterEntitySupport = new MasterEntitySupport<>(getFilterSupport(), null,
                sm -> artifactsEncrypted = sm != null && sm.isEncrypted());

        init();
    }

    private void initFilterMappings() {
        getFilterSupport().<Long> addMapping(FilterType.MASTER,
                (filter, masterFilter) -> getFilterSupport().setFilter(masterFilter));
    }

    /**
     * Initial method of grid and set style name
     */
    @Override
    public void init() {
        super.init();

        addClassNames("grid-row-border");
    }

    private boolean artifactsDeletionCallback(final Collection<ProxyArtifact> artifactsToBeDeleted) {
        final Collection<Long> artifactToBeDeletedIds = artifactsToBeDeleted.stream()
                .map(ProxyIdentifiableEntity::getId).collect(Collectors.toList());
        artifactToBeDeletedIds.forEach(artifactManagement::delete);

        eventBus.publish(EventTopics.ENTITY_MODIFIED, this, new EntityModifiedEventPayload(
                EntityModifiedEventType.ENTITY_UPDATED, ProxySoftwareModule.class, masterEntitySupport.getMasterId()));

        return true;
    }

    /**
     * @return ID for artifact details table
     */
    @Override
    public String getGridId() {
        return UIComponentIdProvider.UPLOAD_ARTIFACT_DETAILS_TABLE;
    }

    /**
     * setting up the columns with their required definition
     */
    @Override
    public void addColumns() {
        addFilenameColumn().setFlexGrow(2);

        addSizeColumn();

        addModifiedDateColumn();

        GridComponentBuilder.joinToActionColumn(i18n, getHeaderRows().get(0),
                Arrays.asList(addDownloadColumn(), addDeleteColumn()));
    }

    private Column<ProxyArtifact> addFilenameColumn() {
        return GridComponentBuilder.addColumn(this, ProxyArtifact::getFilename).setKey(ARTIFACT_NAME_ID)
                .setHeader(i18n.getMessage("artifact.filename.caption"));
    }

    private Column<ProxyArtifact> addSizeColumn() {
        return GridComponentBuilder.addColumn(this, ProxyArtifact::getSize).setKey(ARTIFACT_SIZE_ID)
                .setHeader(i18n.getMessage("artifact.filesize.bytes.caption"));
    }

    protected Column<ProxyArtifact> addModifiedDateColumn() {
        return GridComponentBuilder.addColumn(this, ProxyArtifact::getModifiedDate).setKey(ARTIFACT_MODIFIED_DATE_ID)
                .setHeader(i18n.getMessage("upload.last.modified.date"));
    }

    private Column<ProxyArtifact> addDownloadColumn() {
        return GridComponentBuilder.addIconColumn(this, this::buildDownloadButton, ARTIFACT_DOWNLOAD_BUTTON_ID,
                i18n.getMessage("header.action.download"));
    }

    private Anchor buildDownloadButton(final ProxyArtifact artifact) {
        final Button downloadButton = GridComponentBuilder.buildActionButton(i18n, clickEvent -> {
        }, VaadinIcon.DOWNLOAD, UIMessageIdProvider.TOOLTIP_ARTIFACT_DOWNLOAD,
                SPUIStyleDefinitions.STATUS_ICON_NEUTRAL,
                UIComponentIdProvider.ARTIFACT_FILE_DOWNLOAD_ICON + "." + artifact.getId(),
                permissionChecker.hasDownloadRepositoryPermission());

        return attachFileDownloader(artifact, downloadButton);
    }

    // Vaadin 8 FileDownloader(StreamResource) extended the button; Flow: the
    // button sits in an anchor whose href is a DownloadHandler (attachment)
    private Anchor attachFileDownloader(final ProxyArtifact artifact, final Button downloadButton) {
        final DownloadHandler artifactDownloadHandler = event -> {
            try {
                final Optional<DbArtifact> artifactBinary = artifactManagement.loadArtifactBinary(
                        artifact.getSha1Hash(), masterEntitySupport.getMasterId(), artifactsEncrypted);
                if (artifactBinary.isEmpty()) {
                    // Vaadin 8: a StreamResource without a stream gave no
                    // download
                    event.getResponse().setStatus(HttpStatusCode.NOT_FOUND.getCode());
                    return;
                }

                event.setFileName(artifact.getFilename());
                event.setContentType(event.getSession().getService().getMimeType(artifact.getFilename()));
                try (InputStream artifactStream = artifactBinary.get().getFileInputStream()) {
                    artifactStream.transferTo(event.getOutputStream());
                }
            } catch (final IOException | RuntimeException e) {
                onDownloadFailure(event.getUI(), artifact, e);
            }
        };

        final Anchor downloadAnchor = new Anchor();
        downloadAnchor.setHref(artifactDownloadHandler, AttachmentType.DOWNLOAD);
        downloadAnchor.add(downloadButton);
        // a disabled Vaadin 8 button did not start the download
        downloadAnchor.setEnabled(downloadButton.isEnabled());

        return downloadAnchor;
    }

    private void onDownloadFailure(final UI ui, final ProxyArtifact artifact, final Exception failure) {
        LOG.error("Download failed for artifact with id {}, filename {}", artifact.getId(), artifact.getFilename(),
                failure);
        if (ui == null) {
            return;
        }
        // the download runs outside the session lock
        ui.access(() -> {
            notification.displayValidationError(i18n.getMessage(UIMessageIdProvider.ARTIFACT_DOWNLOAD_FAILURE_MSG));
            // give error details extractors a chance to process specific
            // error
            throw new DownloadException(failure);
        });
    }

    private Column<ProxyArtifact> addDeleteColumn() {
        return GridComponentBuilder.addDeleteColumn(this, i18n, ARTIFACT_DELETE_BUTTON_ID, artifactDeleteSupport,
                UIComponentIdProvider.ARTIFACT_DELET_ICON, e -> permissionChecker.hasDeleteRepositoryPermission());
    }

    @Override
    protected void addMaxColumns() {
        addFilenameColumn().setFlexGrow(2);

        addSizeColumn();

        addSha1Column();
        addMd5Column();
        addSha256Column();

        addModifiedDateColumn();

        GridComponentBuilder.joinToActionColumn(i18n, getHeaderRows().get(0),
                Arrays.asList(addDownloadColumn(), addDeleteColumn()));

        // Vaadin 8: setHidable(true) on every column (the user could hide
        // columns); Flow grids have no column hiding menu
    }

    private Column<ProxyArtifact> addSha1Column() {
        return GridComponentBuilder.addColumn(this, ProxyArtifact::getSha1Hash).setKey(ARTIFACT_SHA1_ID)
                .setHeader(i18n.getMessage("upload.sha1"));
    }

    private Column<ProxyArtifact> addMd5Column() {
        return GridComponentBuilder.addColumn(this, ProxyArtifact::getMd5Hash).setKey(ARTIFACT_MD5_ID)
                .setHeader(i18n.getMessage("upload.md5"));
    }

    private Column<ProxyArtifact> addSha256Column() {
        return GridComponentBuilder.addColumn(this, ProxyArtifact::getSha256Hash).setKey(ARTIFACT_SHA256_ID)
                .setHeader(i18n.getMessage("upload.sha256"));
    }

    /**
     * @return software module entity
     */
    public MasterEntitySupport<ProxySoftwareModule> getMasterEntitySupport() {
        return masterEntitySupport;
    }

    private static class DownloadException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public DownloadException(final Throwable cause) {
            super(cause);
        }
    }
}
